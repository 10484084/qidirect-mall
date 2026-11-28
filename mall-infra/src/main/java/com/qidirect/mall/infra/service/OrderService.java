package com.qidirect.mall.infra.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.common.constant.SysConfigKeys;
import com.qidirect.mall.common.enums.CommissionBizType;
import com.qidirect.mall.common.enums.PerformanceBizType;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.dto.admin.OrderShipRequest;
import com.qidirect.mall.domain.dto.store.OrderCreateRequest;
import com.qidirect.mall.domain.entity.CartItem;
import com.qidirect.mall.domain.entity.OrderInfo;
import com.qidirect.mall.domain.entity.OrderItem;
import com.qidirect.mall.domain.entity.Payment;
import com.qidirect.mall.domain.entity.Product;
import com.qidirect.mall.domain.entity.ProductSku;
import com.qidirect.mall.domain.entity.SkuLevelPrice;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.domain.entity.StoreEvent;
import com.qidirect.mall.domain.vo.GiftRewardVO;
import com.qidirect.mall.domain.vo.OrderItemVO;
import com.qidirect.mall.domain.vo.OrderVO;
import com.qidirect.mall.infra.mapper.CartItemMapper;
import com.qidirect.mall.infra.mapper.OrderInfoMapper;
import com.qidirect.mall.infra.mapper.OrderItemMapper;
import com.qidirect.mall.infra.mapper.PaymentMapper;
import com.qidirect.mall.infra.mapper.ProductMapper;
import com.qidirect.mall.infra.mapper.ProductSkuMapper;
import com.qidirect.mall.infra.mapper.SkuLevelPriceMapper;
import com.qidirect.mall.infra.mapper.StoreEventMapper;
import com.qidirect.mall.infra.mapper.StoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 订货订单：购物车结算、预存抵扣、在线支付、两阶段取消、发货与确认收货。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PAID = 1;
    private static final int STATUS_SHIPPED = 2;
    private static final int STATUS_FINISHED = 3;
    private static final int STATUS_CANCELED = 4;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final SkuLevelPriceMapper skuLevelPriceMapper;
    private final PaymentMapper paymentMapper;
    private final StoreMapper storeMapper;
    private final StoreEventMapper storeEventMapper;
    private final StoreGuard storeGuard;
    private final WalletService walletService;
    private final CommissionService commissionService;
    private final PerformanceService performanceService;
    private final GiftRuleService giftRuleService;
    private final SysConfigService sysConfigService;
    private final PaymentGateway paymentGateway;
    private final NoticeService noticeService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 下单事务内重新核算等级价与赠品，不 trust 前台金额；仅删除本次结算的购物车行。
     */
    @Transactional
    public OrderVO create(Long storeId, OrderCreateRequest request) {
        Store store = storeGuard.assertCanOrder(storeId);
        List<Long> cartIds = request.cartItemIds().stream().filter(Objects::nonNull).distinct().toList();
        if (cartIds.isEmpty() || cartIds.size() != request.cartItemIds().size()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "结算购物车记录不合法");
        }
        List<CartItem> cartItems = cartItemMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getStoreId, storeId)
                .in(CartItem::getId, cartIds)
                .orderByAsc(CartItem::getId));
        if (cartItems.size() != cartIds.size()) {
            throw new BizException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        long totalAmount = 0L;
        List<PaidLine> paidLines = new ArrayList<>();
        Map<Long, Integer> productBottles = new LinkedHashMap<>();
        for (CartItem cartItem : cartItems) {
            Product product = productMapper.selectById(cartItem.getProductId());
            if (product == null) {
                throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
            }
            if (!Objects.equals(product.getStatus(), 1)) {
                throw new BizException(ErrorCode.PRODUCT_OFF_SHELF);
            }
            ProductSku sku = productSkuMapper.selectById(cartItem.getSkuId());
            if (sku == null || !Objects.equals(sku.getProductId(), product.getId())) {
                throw new BizException(ErrorCode.SKU_NOT_FOUND);
            }
            SkuLevelPrice levelPrice = skuLevelPriceMapper.selectOne(Wrappers.<SkuLevelPrice>lambdaQuery()
                    .eq(SkuLevelPrice::getSkuId, sku.getId())
                    .eq(SkuLevelPrice::getLevelId, store.getLevelId())
                    .last("limit 1"));
            if (levelPrice == null) {
                throw new BizException(ErrorCode.SKU_PRICE_MISSING);
            }
            paidLines.add(new PaidLine(product.getId(), sku.getId(), sku.getSpecName(),
                    levelPrice.getPrice(), cartItem.getQty()));
            totalAmount += levelPrice.getPrice() * cartItem.getQty();
            productBottles.merge(product.getId(), sku.getFactor() * cartItem.getQty(), Integer::sum);
        }
        long depositAmount = 0L;
        if (request.depositEnabled() && totalAmount > 0) {
            depositAmount = Math.min(totalAmount, walletService.getWallet(storeId).depositBalance());
        }
        long onlineAmount = totalAmount - depositAmount;
        // 买赠只对使用了预存余额的订单生效：余额不足时允许「预存 + 微信」组合支付并照常享受，
        // 余额为 0 只能全额在线支付，此时不参与买赠。
        List<GiftRewardVO> gifts = depositAmount > 0
                ? giftRuleService.calculateGifts(store, productBottles)
                : List.of();
        LocalDateTime now = LocalDateTime.now();

        OrderInfo order = new OrderInfo();
        order.setOrderNo(newNo("O"));
        order.setStoreId(storeId);
        order.setTotalAmount(totalAmount);
        order.setDepositAmount(depositAmount);
        order.setOnlineAmount(onlineAmount);
        order.setStatus(onlineAmount == 0 ? STATUS_PAID : STATUS_PENDING);
        order.setReceiverName(request.receiverName().trim());
        order.setReceiverPhone(request.receiverPhone().trim());
        order.setReceiverAddr(request.receiverAddr().trim());
        order.setShipType(0);
        order.setShipCompany("");
        order.setShipNo("");
        order.setShipRemark("");
        order.setPayTime(onlineAmount == 0 ? now : null);
        orderInfoMapper.insert(order);

        for (PaidLine line : paidLines) {
            insertItem(order.getId(), line.productId(), line.skuId(), line.specName(),
                    line.price(), line.qty(), 0);
        }
        for (GiftRewardVO gift : gifts) {
            insertItem(order.getId(), gift.productId(), gift.skuId(), gift.specName(), 0L, gift.qty(), 1);
        }

        insertEvent(order, "ORDER", "订单已创建", totalAmount, 0, 0,
                1, 0L, null, null);
        noticeService.notifyAdmins("新订货订单",
                "门店「" + StrUtil.blankToDefault(store.getName(), store.getNickname())
                        + "」提交订单 " + order.getOrderNo() + "，金额 " + yuan(totalAmount),
                "ORDER", order.getOrderNo());
        if (depositAmount > 0) {
            walletService.deductDepositForOrder(storeId, depositAmount, order.getOrderNo());
        }
        if (onlineAmount > 0) {
            Payment payment = new Payment();
            payment.setPayNo(newNo("P"));
            payment.setBizType("ORDER");
            payment.setBizId(order.getId());
            payment.setStoreId(storeId);
            payment.setAmount(onlineAmount);
            payment.setChannel(1);
            payment.setOfflineMethod(0);
            payment.setStatus(0);
            payment.setOutTradeNo("");
            payment.setRefundAmount(0L);
            payment.setRefundNo("");
            payment.setRefundBy(0L);
            payment.setExpireAt(now.plusMinutes(orderTimeoutMinutes()));
            paymentMapper.insert(payment);
        } else {
            // 全额预存抵扣：业绩与提成已在储值时结算（储值不退），此处不重复计
            insertEvent(order, "ORDER_PAID", "订单已结算，等待发货", depositAmount, 0, 0,
                    3, 0L, null, null);
        }

        cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getStoreId, storeId)
                .in(CartItem::getId, cartIds));
        return toVO(requireOrder(order.getId()), false, true);
    }

    public PageResult<OrderVO> storePage(Long storeId, Integer status, long page, long size) {
        storeGuard.requireStore(storeId);
        validateStatus(status);
        LambdaQueryWrapper<OrderInfo> query = Wrappers.<OrderInfo>lambdaQuery()
                .eq(OrderInfo::getStoreId, storeId)
                .eq(status != null, OrderInfo::getStatus, status)
                .orderByDesc(OrderInfo::getId);
        IPage<OrderInfo> result = orderInfoMapper.selectPage(new Page<>(pageNo(page), pageSize(size)), query);
        List<OrderVO> records = result.getRecords().stream().map(order -> toVO(order, false, true)).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public OrderVO storeDetail(Long storeId, Long orderId) {
        return toVO(requireOwned(storeId, orderId), false, true);
    }

    public PageResult<OrderVO> adminPage(String orderNo, Long storeId, Integer status,
                                         LocalDateTime startTime, LocalDateTime endTime,
                                         long page, long size) {
        IPage<OrderInfo> result = orderInfoMapper.selectPage(new Page<>(pageNo(page), pageSize(size)),
                adminQuery(orderNo, storeId, status, startTime, endTime));
        List<OrderVO> records = result.getRecords().stream().map(order -> toVO(order, true, false)).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public OrderVO adminDetail(Long orderId) {
        return toVO(requireOrder(orderId), true, true);
    }

    public List<OrderVO> adminExport(String orderNo, Long storeId, Integer status,
                                     LocalDateTime startTime, LocalDateTime endTime) {
        return orderInfoMapper.selectList(adminQuery(orderNo, storeId, status, startTime, endTime))
                .stream().map(order -> toVO(order, true, false)).toList();
    }

    /**
     * 支付成功分发入口。支付单与业务单条件更新保证重复回调不会重复处理。
     */
    @Transactional
    public void handlePaid(Long paymentId) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null || !"ORDER".equals(payment.getBizType())
                || payment.getStatus() == null || payment.getStatus() != 2) {
            return;
        }
        markOrderPaid(payment);
    }

    /**
     * 关单完成后才到达的成功回调：订货单无异常态，写入事件并告警等待人工核查。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markAbnormalPaid(Payment payment) {
        OrderInfo order = orderInfoMapper.selectById(payment.getBizId());
        if (order == null || !Objects.equals(order.getStatus(), STATUS_CANCELED)) {
            return;
        }
        log.error("已取消订单收到支付成功结果，准备自动退款：orderNo={} payNo={}",
                order.getOrderNo(), payment.getPayNo());
        if (order.getPayTime() == null) {
            // 回写支付时间，自动退款失败转人工时仍可按正常流程处理
            orderInfoMapper.update(null, Wrappers.<OrderInfo>lambdaUpdate()
                    .eq(OrderInfo::getId, order.getId())
                    .set(OrderInfo::getPayTime, payment.getPaidAt() == null ? LocalDateTime.now() : payment.getPaidAt()));
        }
        // 订单已关闭，用户仍完成支付：立即原路退回，避免资金滞留在渠道侧等人工处理
        String outRefundNo = "AR" + payment.getPayNo();
        try {
            PaymentGateway.RefundResult result = paymentGateway.refund(payment, payment.getAmount(), outRefundNo);
            if (result.success()) {
                paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                        .eq(Payment::getId, payment.getId())
                        .eq(Payment::getStatus, 2)
                        .set(Payment::getStatus, 3)
                        .set(Payment::getRefundAt, LocalDateTime.now())
                        .set(Payment::getRefundAmount, payment.getAmount())
                        .set(Payment::getRefundNo, StrUtil.blankToDefault(result.refundNo(), outRefundNo))
                        .set(Payment::getRefundBy, 0L));
                insertEvent(order, "PAY_ABNORMAL_REFUNDED", "订单已取消但支付成功，已自动原路退款",
                        payment.getAmount(), 1, 1, 3, 0L,
                        JSONUtil.toJsonStr(Map.of("payNo", payment.getPayNo(), "refundNo", outRefundNo)), null);
                noticeService.notifyAdmins("已自动退款",
                        "订单 " + order.getOrderNo() + " 已取消但收到支付，已自动原路退款",
                        "PAY_ABNORMAL", order.getOrderNo());
                return;
            }
            log.error("自动退款未成功：orderNo={} msg={}", order.getOrderNo(), result.message());
        } catch (Exception e) {
            log.error("自动退款调用异常：orderNo={} err={}", order.getOrderNo(), e.toString());
        }
        insertEvent(order, "PAY_ABNORMAL", "订单已取消但支付成功，自动退款失败需人工核查",
                payment.getAmount(), 1, 1, 3, 0L,
                JSONUtil.toJsonStr(Map.of("payNo", payment.getPayNo())), null);
        noticeService.notifyAdmins("异常订单待处理",
                "订单 " + order.getOrderNo() + " 已取消但支付成功，自动退款失败需人工处理",
                "PAY_ABNORMAL", order.getOrderNo());
    }

    @Transactional
    public void confirmReceipt(Long storeId, Long orderId) {
        OrderInfo order = requireOwned(storeId, orderId);
        int claimed = orderInfoMapper.update(null, Wrappers.<OrderInfo>lambdaUpdate()
                .eq(OrderInfo::getId, order.getId())
                .eq(OrderInfo::getStatus, STATUS_SHIPPED)
                .set(OrderInfo::getStatus, STATUS_FINISHED)
                .set(OrderInfo::getFinishTime, LocalDateTime.now()));
        if (claimed == 0) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
        }
        insertEvent(order, "ORDER_FINISH", "订单已确认收货", 0L, 0, 0,
                1, 0L, null, null);
    }

    @Transactional
    public OrderVO ship(Long orderId, OrderShipRequest request, Long operatorId) {
        OrderInfo order = requireOrder(orderId);
        String company = "";
        String shipNo = "";
        String images = JSONUtil.toJsonStr(request.shipImages() == null ? List.of() : request.shipImages());
        if (request.shipType() == 1) {
            if (StrUtil.isBlank(request.shipCompany()) || StrUtil.isBlank(request.shipNo())) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "物流发货必须填写物流公司与单号");
            }
            company = request.shipCompany().trim();
            shipNo = request.shipNo().trim();
        } else if (request.shipImages() == null || request.shipImages().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "其他发货方式必须上传凭证");
        }

        int claimed = orderInfoMapper.update(null, Wrappers.<OrderInfo>lambdaUpdate()
                .eq(OrderInfo::getId, orderId)
                .eq(OrderInfo::getStatus, STATUS_PAID)
                .set(OrderInfo::getStatus, STATUS_SHIPPED)
                .set(OrderInfo::getShipType, request.shipType())
                .set(OrderInfo::getShipCompany, company)
                .set(OrderInfo::getShipNo, shipNo)
                .set(OrderInfo::getShipImages, images)
                .set(OrderInfo::getShipRemark, StrUtil.nullToEmpty(request.remark()))
                .set(OrderInfo::getShipTime, LocalDateTime.now()));
        if (claimed == 0) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
        }
        insertEvent(order, "SHIP", "订单已发货", 0L, 0, 0,
                2, operatorId, null, images);
        noticeService.notifyStore(order.getStoreId(), "订单已发货",
                "订单 " + order.getOrderNo() + " 已发货，请注意查收", "SHIP",
                order.getOrderNo(), NoticeService.SMS_SHIP);
        return toVO(requireOrder(orderId), true, true);
    }

    /**
     * 门店取消未支付订单。外部关单与查单不在事务内执行。
     */
    public OrderVO cancelForStore(Long storeId, Long orderId) {
        OrderInfo order = requireOwned(storeId, orderId);
        if (!Objects.equals(order.getStatus(), STATUS_PENDING)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "订单已支付，请在订单详情申请取消退款");
        }
        cancel(order, 1, 0L);
        return storeDetail(storeId, orderId);
    }

    /**
     * 超时任务复用门店取消的完整关单、查单与退款流程。
     */
    public int cancelTimeoutOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(orderTimeoutMinutes());
        List<OrderInfo> orders = orderInfoMapper.selectList(Wrappers.<OrderInfo>lambdaQuery()
                .eq(OrderInfo::getStatus, STATUS_PENDING)
                .le(OrderInfo::getCreatedAt, deadline)
                .orderByAsc(OrderInfo::getId)
                .last("limit 100"));
        int handled = 0;
        for (OrderInfo order : orders) {
            try {
                cancel(order, 3, 0L);
                handled++;
            } catch (Exception e) {
                log.error("超时订单取消失败：orderId={} orderNo={} error={}",
                        order.getId(), order.getOrderNo(), e.toString());
            }
        }
        return handled;
    }

    private void cancel(OrderInfo order, int operatorType, Long operatorId) {
        OrderInfo current = requireOrder(order.getId());
        if (!Objects.equals(current.getStatus(), STATUS_PENDING)) {
            return;
        }
        Payment payment = findPayment(current.getId());
        if (payment == null) {
            if (current.getOnlineAmount() != null && current.getOnlineAmount() > 0) {
                throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "订单支付单缺失，无法自动取消");
            }
            transactionTemplate.executeWithoutResult(status ->
                    finalizeCancel(current.getId(), null, operatorType, operatorId));
            return;
        }

        // 事务 1：支付行锁内确认业务仍待支付，并将待支付单推进到支付中，避免新增支付发起。
        Payment prepared = transactionTemplate.execute(status -> {
            OrderInfo lockedOrder = orderInfoMapper.selectById(current.getId());
            if (lockedOrder == null || !Objects.equals(lockedOrder.getStatus(), STATUS_PENDING)) {
                return null;
            }
            Payment lockedPayment = paymentMapper.selectForUpdate(payment.getId());
            if (lockedPayment == null) {
                throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "订单支付单缺失，无法自动取消");
            }
            if (Objects.equals(lockedPayment.getStatus(), 0)) {
                paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                        .eq(Payment::getId, lockedPayment.getId())
                        .eq(Payment::getStatus, 0)
                        .set(Payment::getStatus, 1));
                lockedPayment.setStatus(1);
            }
            return lockedPayment;
        });
        if (prepared == null) {
            return;
        }
        if (Objects.equals(prepared.getStatus(), 2)) {
            transactionTemplate.executeWithoutResult(status -> markOrderPaid(prepared));
            return;
        }

        // 事务外调用 4.8 与 4.5。网关侧失败不阻断本地关单：订单生成后门店未必点过支付，
        // 此时扫呗并无该笔订单，关单必然失败；若因此退回预存会被长期锁死。
        try {
            paymentGateway.close(prepared);
        } catch (Exception e) {
            log.warn("关单失败，继续查单确认：payNo={} err={}", prepared.getPayNo(), e.getMessage());
        }
        PaymentGateway.PaymentQueryResult queried;
        try {
            queried = paymentGateway.query(prepared);
        } catch (Exception e) {
            log.warn("查单失败，按未支付处理并本地关单：payNo={} err={}", prepared.getPayNo(), e.getMessage());
            queried = new PaymentGateway.PaymentQueryResult(false, null, null);
        }
        final PaymentGateway.PaymentQueryResult queryResult = queried;

        if (queryResult.paid()) {
            // 查单已支付：支付行锁内补记成功，绝不退预存或取消订单。
            transactionTemplate.executeWithoutResult(status -> {
                Payment locked = paymentMapper.selectForUpdate(payment.getId());
                if (locked == null) {
                    throw new BizException(ErrorCode.NOT_FOUND.getCode(), "支付单不存在");
                }
                LocalDateTime paidAt = queryResult.paidAt() == null ? LocalDateTime.now() : queryResult.paidAt();
                if (!Objects.equals(locked.getStatus(), 2)) {
                    paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                            .eq(Payment::getId, locked.getId())
                            .set(Payment::getStatus, 2)
                            .set(Payment::getOutTradeNo, StrUtil.nullToEmpty(queryResult.outTradeNo()))
                            .set(Payment::getPaidAt, paidAt));
                }
                locked.setStatus(2);
                locked.setPaidAt(locked.getPaidAt() == null ? paidAt : locked.getPaidAt());
                markOrderPaid(locked);
            });
            return;
        }

        // 事务 2：再次锁支付行确认未支付，然后条件取消、退预存并关闭支付单。
        transactionTemplate.executeWithoutResult(status ->
                finalizeCancel(current.getId(), payment.getId(), operatorType, operatorId));
    }

    private void finalizeCancel(Long orderId, Long paymentId, int operatorType, Long operatorId) {
        Payment payment = paymentId == null ? null : paymentMapper.selectForUpdate(paymentId);
        OrderInfo order = orderInfoMapper.selectById(orderId);
        if (order == null || !Objects.equals(order.getStatus(), STATUS_PENDING)) {
            return;
        }
        if (payment != null && Objects.equals(payment.getStatus(), 2)) {
            markOrderPaid(payment);
            return;
        }
        int claimed = orderInfoMapper.update(null, Wrappers.<OrderInfo>lambdaUpdate()
                .eq(OrderInfo::getId, orderId)
                .eq(OrderInfo::getStatus, STATUS_PENDING)
                .set(OrderInfo::getStatus, STATUS_CANCELED));
        if (claimed == 0) {
            return;
        }
        if (order.getDepositAmount() != null && order.getDepositAmount() > 0) {
            walletService.refundDepositForOrder(order.getStoreId(), order.getDepositAmount(), order.getOrderNo());
        }
        if (payment != null) {
            paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                    .eq(Payment::getId, payment.getId())
                    .in(Payment::getStatus, 0, 1, 3)
                    .set(Payment::getStatus, 4));
        }
        insertEvent(order, "ORDER_CANCEL", "订单已取消", order.getDepositAmount(), 1, 1,
                operatorType, operatorId, null, null);
    }

    private void markOrderPaid(Payment payment) {
        OrderInfo order = orderInfoMapper.selectById(payment.getBizId());
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!Objects.equals(order.getStoreId(), payment.getStoreId())
                || !Objects.equals(order.getOnlineAmount(), payment.getAmount())) {
            throw new BizException(ErrorCode.PAY_NOTIFY_INVALID.getCode(), "订单支付金额或归属不一致");
        }
        if (!Objects.equals(order.getStatus(), STATUS_PENDING)) {
            if (Objects.equals(order.getStatus(), STATUS_CANCELED)) {
                log.error("已取消订单收到支付成功结果，需人工核查：orderNo={} payNo={}",
                        order.getOrderNo(), payment.getPayNo());
            }
            return;
        }
        LocalDateTime paidAt = payment.getPaidAt() == null ? LocalDateTime.now() : payment.getPaidAt();
        int claimed = orderInfoMapper.update(null, Wrappers.<OrderInfo>lambdaUpdate()
                .eq(OrderInfo::getId, order.getId())
                .eq(OrderInfo::getStatus, STATUS_PENDING)
                .set(OrderInfo::getStatus, STATUS_PAID)
                .set(OrderInfo::getPayTime, paidAt));
        if (claimed == 0) {
            return;
        }
        insertEvent(order, "ORDER_PAID", "订单支付成功，等待发货", order.getOnlineAmount(), 0, 0,
                3, 0L, null, null);
        commissionService.dispatch(CommissionBizType.ORDER, order.getOrderNo(),
                order.getStoreId(), payment.getAmount());
        performanceService.record(PerformanceBizType.ORDER, order.getOrderNo(),
                order.getStoreId(), payment.getAmount(), paidAt);
    }

    private LambdaQueryWrapper<OrderInfo> adminQuery(String orderNo, Long storeId, Integer status,
                                                     LocalDateTime startTime, LocalDateTime endTime) {
        validateStatus(status);
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "开始时间不能晚于结束时间");
        }
        return Wrappers.<OrderInfo>lambdaQuery()
                .like(StrUtil.isNotBlank(orderNo), OrderInfo::getOrderNo, orderNo)
                .eq(storeId != null, OrderInfo::getStoreId, storeId)
                .eq(status != null, OrderInfo::getStatus, status)
                .ge(startTime != null, OrderInfo::getCreatedAt, startTime)
                .le(endTime != null, OrderInfo::getCreatedAt, endTime)
                .orderByDesc(OrderInfo::getId);
    }

    private OrderVO toVO(OrderInfo order, boolean includeStore, boolean includeItems) {
        Store store = includeStore ? storeMapper.selectById(order.getStoreId()) : null;
        Payment payment = findPayment(order.getId());
        List<OrderItemVO> items = includeItems ? orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                        .eq(OrderItem::getOrderId, order.getId())
                        .orderByAsc(OrderItem::getId))
                .stream().map(this::toItemVO).toList() : List.of();
        return new OrderVO(order.getId(), order.getOrderNo(), order.getStoreId(),
                store == null ? null : store.getName(), store == null ? null : store.getPhone(),
                order.getTotalAmount(), order.getDepositAmount(), order.getOnlineAmount(), order.getStatus(),
                order.getReceiverName(), order.getReceiverPhone(), order.getReceiverAddr(),
                order.getShipType(), order.getShipCompany(), order.getShipNo(),
                parseImages(order.getShipImages()), order.getShipRemark(), order.getPayTime(),
                order.getShipTime(), order.getFinishTime(), order.getCreatedAt(),
                payment == null ? null : payment.getPayNo(), items);
    }

    private OrderItemVO toItemVO(OrderItem item) {
        Product product = productMapper.selectById(item.getProductId());
        return new OrderItemVO(item.getProductId(), product == null ? "" : product.getName(),
                item.getSkuId(), item.getSpecName(), item.getPrice(), item.getQty(), item.getIsGift(),
                item.getPrice() * item.getQty());
    }

    private void insertItem(Long orderId, Long productId, Long skuId, String specName,
                            Long price, Integer qty, int isGift) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setSkuId(skuId);
        item.setSpecName(specName);
        item.setPrice(price);
        item.setQty(qty);
        item.setIsGift(isGift);
        orderItemMapper.insert(item);
    }

    private void insertEvent(OrderInfo order, String type, String title, Long amount,
                             int direction, int account, int operatorType, Long operatorId,
                             String detail, String proofImages) {
        StoreEvent event = new StoreEvent();
        event.setStoreId(order.getStoreId());
        event.setEventType(type);
        event.setBizNo(order.getOrderNo());
        event.setTitle(title);
        event.setAmount(amount == null ? 0L : amount);
        event.setDirection(direction);
        event.setAccount(account);
        event.setDetail(detail);
        event.setProofImages(proofImages);
        event.setOperatorType(operatorType);
        event.setOperatorId(operatorId == null ? 0L : operatorId);
        storeEventMapper.insert(event);
    }

    private Payment findPayment(Long orderId) {
        return paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getBizType, "ORDER")
                .eq(Payment::getBizId, orderId)
                .orderByDesc(Payment::getId)
                .last("limit 1"));
    }

    private OrderInfo requireOwned(Long storeId, Long orderId) {
        OrderInfo order = requireOrder(orderId);
        if (!Objects.equals(order.getStoreId(), storeId)) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private OrderInfo requireOrder(Long orderId) {
        OrderInfo order = orderInfoMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private int orderTimeoutMinutes() {
        return Math.max(1, sysConfigService.getInt(SysConfigKeys.ORDER_TIMEOUT_MINUTES, 10));
    }

    private void validateStatus(Integer status) {
        if (status != null && (status < STATUS_PENDING || status > STATUS_CANCELED)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "订单状态不正确");
        }
    }

    private long pageNo(long page) {
        return Math.max(page, 1);
    }

    private long pageSize(long size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private List<String> parseImages(String images) {
        if (StrUtil.isBlank(images)) {
            return List.of();
        }
        try {
            return JSONUtil.toList(images, String.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String newNo(String prefix) {
        return prefix + NO_FORMAT.format(LocalDateTime.now()) + RandomUtil.randomNumbers(4);
    }

    private String yuan(long amount) {
        return java.math.BigDecimal.valueOf(amount, 2).toPlainString() + " 元";
    }

    private record PaidLine(Long productId, Long skuId, String specName, Long price, Integer qty) {
    }
}

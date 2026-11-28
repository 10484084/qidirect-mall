package com.qidirect.mall.infra.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.common.constant.SysConfigKeys;
import com.qidirect.mall.common.enums.CommissionBizType;
import com.qidirect.mall.common.enums.PerformanceBizType;
import com.qidirect.mall.common.enums.WalletBizType;
import com.qidirect.mall.common.enums.WalletSource;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.dto.admin.OfflineCollectRequest;
import com.qidirect.mall.domain.dto.admin.ProxyJoinRequest;
import com.qidirect.mall.domain.dto.store.PurchaseRequest;
import com.qidirect.mall.domain.entity.Agreement;
import com.qidirect.mall.domain.entity.JoinOrder;
import com.qidirect.mall.domain.entity.Payment;
import com.qidirect.mall.domain.entity.RechargeActivity;
import com.qidirect.mall.domain.entity.RechargeActivityTier;
import com.qidirect.mall.domain.entity.RechargeOrder;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.domain.entity.StoreEvent;
import com.qidirect.mall.domain.entity.StoreLevel;
import com.qidirect.mall.domain.entity.StoreLevelLog;
import com.qidirect.mall.domain.vo.AgreementVO;
import com.qidirect.mall.domain.vo.PurchaseOptionVO;
import com.qidirect.mall.domain.vo.PurchaseOrderVO;
import com.qidirect.mall.infra.mapper.AgreementMapper;
import com.qidirect.mall.infra.mapper.JoinOrderMapper;
import com.qidirect.mall.infra.mapper.PaymentMapper;
import com.qidirect.mall.infra.mapper.RechargeActivityMapper;
import com.qidirect.mall.infra.mapper.RechargeActivityTierMapper;
import com.qidirect.mall.infra.mapper.RechargeOrderMapper;
import com.qidirect.mall.infra.mapper.StoreEventMapper;
import com.qidirect.mall.infra.mapper.StoreLevelLogMapper;
import com.qidirect.mall.infra.mapper.StoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Map;

/**
 * 统一购买：开通 / 补差升级 / 预存，三条路径由判定表决定。
 * 开通费、补差与预存款在支付成功后全额转入预存余额；补差与超期升档规则见 spec 3.3。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_CANCELED = 2;
    private static final int STATUS_ABNORMAL = 3;
    /** 支付单状态：支付中（与订单状态含义不同） */
    private static final int PAY_STATUS_PAYING = 1;
    /** 支付单状态：已关闭 */
    private static final int PAY_STATUS_CLOSED = 4;
    private static final int CHANNEL_JSAPI = 1;
    private static final int CHANNEL_OFFLINE = 3;
    private static final int SIGN_ONLINE = 1;
    private static final int SIGN_PAPER = 2;
    private static final int DEFAULT_UPGRADE_DAYS = 60;
    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final String SIGN_KEY = "purchase:sign:";

    private final StoreMapper storeMapper;
    private final JoinOrderMapper joinOrderMapper;
    private final RechargeOrderMapper rechargeOrderMapper;
    private final PaymentMapper paymentMapper;
    private final StoreLevelLogMapper storeLevelLogMapper;
    private final StoreEventMapper storeEventMapper;
    private final AgreementMapper agreementMapper;
    private final RechargeActivityMapper rechargeActivityMapper;
    private final RechargeActivityTierMapper rechargeActivityTierMapper;
    private final StoreLevelService storeLevelService;
    private final AgreementService agreementService;
    private final StoreGuard storeGuard;
    private final WalletService walletService;
    private final CommissionService commissionService;
    private final PerformanceService performanceService;
    private final OrderService orderService;
    private final SysConfigService sysConfigService;
    private final NoticeService noticeService;
    private final StringRedisTemplate redisTemplate;

    public List<PurchaseOptionVO> options(Long storeId) {
        Store store = storeGuard.requireStore(storeId);
        LocalDateTime now = LocalDateTime.now();
        return storeLevelService.listEnabled().stream()
                .map(vo -> storeLevelService.requireLevel(vo.id()))
                .map(level -> optionFor(store, level, now))
                .toList();
    }

    public AgreementVO agreement() {
        return agreementService.active();
    }

    public PurchaseOptionVO optionFor(Store store, StoreLevel level, LocalDateTime now) {
        PathDecision decision = decide(store, level, now);
        String description;
        if (WalletBizType.JOIN.name().equals(decision.action())) {
            description = "首次开通，全额收款";
        } else if (WalletBizType.UPGRADE.name().equals(decision.action())) {
            description = "窗口期内升级，仅支付差额";
        } else {
            description = isExpiredUpgrade(store, level, now) ? "已超窗口期，需全额支付" : "全额预存";
        }
        return new PurchaseOptionVO(level.getId(), level.getName(), level.getFee(),
                decision.action(), decision.payAmount(), description);
    }

    @Transactional
    public PurchaseOrderVO createOnlineOrder(Long storeId, PurchaseRequest request, String ip) {
        if (!Boolean.TRUE.equals(request.agreed())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "请先阅读并同意协议");
        }
        Store store = storeGuard.assertCanPurchase(storeId);
        StoreLevel level = storeLevelService.requireLevel(request.levelId());
        Agreement agreement = agreementService.requireActive();
        PathDecision decision = decide(store, level, LocalDateTime.now());
        validateActivity(store, level, request.activityId(), decision);

        PaidContext context = new PaidContext(WalletSource.ONLINE, null, 0L, "线上购买",
                SIGN_ONLINE, null, ip, agreement);
        CreatedOrder created = createOrder(store, level, decision, request.activityId(),
                CHANNEL_JSAPI, 0, null, context);
        return toVO(created);
    }

    /**
     * 任务 8 接入扫呗后由支付回调调用；当前供支付 Mock 与线下收款复用。
     */
    @Transactional
    public void handlePaid(Long paymentId) {
        applyPaid(paymentId, PaidContext.online());
    }

    /**
     * 开发联调 Mock：仅把支付单置成功并分发业务，不接真实网关。
     */
    @Transactional
    public void mockPaid(String payNo) {
        Payment payment = paymentMapper.selectForUpdateByPayNo(payNo);
        if (payment == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "支付单不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        int claimed = paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                .eq(Payment::getId, payment.getId())
                .in(Payment::getStatus, 0, 1)
                .set(Payment::getStatus, 2)
                .set(Payment::getPaidAt, now));
        if (claimed == 0) {
            return;
        }
        applyPaid(payment.getId(), PaidContext.online());
    }

    /**
     * 支付回调或补偿在同一事务内分发业务，支付单已置成功。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void dispatchPaid(Long paymentId) {
        applyPaid(paymentId, PaidContext.online());
    }

    /**
     * 关单完成后才到达的成功回调：支付单保持成功，业务单转异常等待人工处置。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markAbnormal(Long paymentId, String reason) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            return;
        }
        if ("ORDER".equals(payment.getBizType())) {
            orderService.markAbnormalPaid(payment);
            return;
        }
        String orderNo = null;
        int claimed = 0;
        try {
            if (WalletBizType.JOIN.name().equals(payment.getBizType())
                    || WalletBizType.UPGRADE.name().equals(payment.getBizType())) {
                JoinOrder order = joinOrderMapper.selectById(payment.getBizId());
                orderNo = order == null ? null : order.getOrderNo();
                claimed = joinOrderMapper.update(null, Wrappers.<JoinOrder>lambdaUpdate()
                        .eq(JoinOrder::getId, payment.getBizId())
                        .in(JoinOrder::getStatus, STATUS_PENDING, STATUS_CANCELED)
                        .set(JoinOrder::getStatus, STATUS_ABNORMAL));
            } else if (WalletBizType.RECHARGE.name().equals(payment.getBizType())) {
                RechargeOrder order = rechargeOrderMapper.selectById(payment.getBizId());
                orderNo = order == null ? null : order.getOrderNo();
                claimed = rechargeOrderMapper.update(null, Wrappers.<RechargeOrder>lambdaUpdate()
                        .eq(RechargeOrder::getId, payment.getBizId())
                        .in(RechargeOrder::getStatus, STATUS_PENDING, STATUS_CANCELED)
                        .set(RechargeOrder::getStatus, STATUS_ABNORMAL));
            }
        } catch (DuplicateKeyException e) {
            log.error("异常单置位冲突，需人工核查：paymentId={} reason={}", paymentId, reason);
            claimed = 1;
        }
        if (claimed == 0) {
            return;
        }
        insertSystemEvent(payment.getStoreId(), "PAY_ABNORMAL", orderNo,
                "关单后收到支付成功，需人工处置", payment.getAmount(), 1,
                Map.of("reason", reason, "payNo", payment.getPayNo()));
        noticeService.notifyAdmins("异常单待处理",
                "门店 " + payment.getStoreId() + " 的 " + payment.getBizType()
                        + " 单据" + (orderNo == null ? "" : " " + orderNo)
                        + " 关单后收到支付成功，需人工处置",
                "PAY_ABNORMAL", orderNo);
    }

    /**
     * 超时关单查单确认未支付后取消开通/补差/预存单，返回是否成功取消。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean cancelUnpaid(Long paymentId) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            return false;
        }
        int claimed;
        String orderNo;
        if (WalletBizType.JOIN.name().equals(payment.getBizType())
                || WalletBizType.UPGRADE.name().equals(payment.getBizType())) {
            JoinOrder order = joinOrderMapper.selectById(payment.getBizId());
            if (order == null) {
                return false;
            }
            orderNo = order.getOrderNo();
            claimed = joinOrderMapper.update(null, Wrappers.<JoinOrder>lambdaUpdate()
                    .eq(JoinOrder::getId, order.getId())
                    .eq(JoinOrder::getStatus, STATUS_PENDING)
                    .set(JoinOrder::getStatus, STATUS_CANCELED));
        } else if (WalletBizType.RECHARGE.name().equals(payment.getBizType())) {
            RechargeOrder order = rechargeOrderMapper.selectById(payment.getBizId());
            if (order == null) {
                return false;
            }
            orderNo = order.getOrderNo();
            claimed = rechargeOrderMapper.update(null, Wrappers.<RechargeOrder>lambdaUpdate()
                    .eq(RechargeOrder::getId, order.getId())
                    .eq(RechargeOrder::getStatus, STATUS_PENDING)
                    .set(RechargeOrder::getStatus, STATUS_CANCELED));
        } else {
            return false;
        }
        if (claimed == 0) {
            return false;
        }
        insertSystemEvent(payment.getStoreId(), payment.getBizType(), orderNo,
                "超时未支付，系统已自动取消", payment.getAmount(), 0,
                Map.of("payNo", payment.getPayNo()));
        return true;
    }

    @Transactional
    public PurchaseOrderVO proxyJoin(ProxyJoinRequest request, Long operatorId, String ip) {
        Store store = storeGuard.requireStore(request.storeId());
        storeGuard.assertNotFrozen(store);
        if (store.getLevelId() != null && store.getLevelId() > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "门店已开通，只能走线下收款或预存/升级");
        }
        StoreLevel level = storeLevelService.requireLevel(request.levelId());
        Agreement agreement = agreementService.requireActive();
        validateOfflineMethod(request.offlineMethod());
        LocalDateTime receivedAt = request.receivedAt() == null ? LocalDateTime.now() : request.receivedAt();
        if (request.idImages() != null && !request.idImages().isEmpty()) {
            Store patch = new Store();
            patch.setId(store.getId());
            patch.setBizImages(JSONUtil.toJsonStr(request.idImages()));
            storeMapper.updateById(patch);
        }
        PathDecision decision = new PathDecision(WalletBizType.JOIN.name(), level.getFee(), level);
        PaidContext context = new PaidContext(WalletSource.OFFLINE,
                jsonArray(request.proofImages()), operatorId,
                StrUtil.blankToDefault(request.remark(), "代客开通"),
                SIGN_PAPER, jsonArray(request.agreementImages()), ip, agreement);
        CreatedOrder created = createOrder(store, level, decision, null,
                CHANNEL_OFFLINE, request.offlineMethod(), receivedAt, context);
        return toVO(created);
    }

    /**
     * 已开通门店线下收款：金额由判定表计算，后台只读。
     */
    @Transactional
    public PurchaseOrderVO offlineCollect(OfflineCollectRequest request, Long operatorId, String ip) {
        Store store = storeGuard.requireStore(request.storeId());
        storeGuard.assertNotFrozen(store);
        if (store.getLevelId() == null || store.getLevelId() == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "未开通门店请使用代客开通");
        }
        StoreLevel level = storeLevelService.requireLevel(request.levelId());
        validateOfflineMethod(request.offlineMethod());
        LocalDateTime receivedAt = request.receivedAt() == null ? LocalDateTime.now() : request.receivedAt();
        PathDecision decision = decide(store, level, receivedAt);
        PaidContext context = new PaidContext(WalletSource.OFFLINE,
                jsonArray(request.proofImages()),
                operatorId, StrUtil.blankToDefault(request.remark(), "线下收款"),
                0, null, ip, null);
        CreatedOrder created = createOrder(store, level, decision, null,
                CHANNEL_OFFLINE, request.offlineMethod(), receivedAt, context);
        return toVO(created);
    }

    private PathDecision decide(Store store, StoreLevel level, LocalDateTime now) {
        if (store.getLevelId() == null || store.getLevelId() == 0) {
            return new PathDecision(WalletBizType.JOIN.name(), level.getFee(), level);
        }
        StoreLevel current = storeLevelService.requireLevel(store.getLevelId());
        boolean windowOpen = store.getUpgradeDeadline() != null && !now.isAfter(store.getUpgradeDeadline());
        if (windowOpen && level.getSort() > current.getSort()) {
            long paid = store.getLevelPaidAmount() == null ? 0L : store.getLevelPaidAmount();
            long diff = level.getFee() - paid;
            if (diff <= 0) {
                log.warn("补差金额非正：storeId={} levelId={} fee={} paid={}",
                        store.getId(), level.getId(), level.getFee(), paid);
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "补差金额必须大于 0，请联系客服");
            }
            return new PathDecision(WalletBizType.UPGRADE.name(), diff, level);
        }
        return new PathDecision(WalletBizType.RECHARGE.name(), level.getFee(), level);
    }

    private boolean isExpiredUpgrade(Store store, StoreLevel level, LocalDateTime now) {
        if (store.getLevelId() == null || store.getLevelId() == 0) {
            return false;
        }
        StoreLevel current = storeLevelService.requireLevel(store.getLevelId());
        boolean windowClosed = store.getUpgradeDeadline() == null || now.isAfter(store.getUpgradeDeadline());
        return windowClosed && level.getSort() > current.getSort();
    }

    private void validateActivity(Store store, StoreLevel level, Long activityId, PathDecision decision) {
        if (activityId == null || activityId == 0 || !WalletBizType.RECHARGE.name().equals(decision.action())) {
            return;
        }
        RechargeActivity activity = rechargeActivityMapper.selectById(activityId);
        if (activity == null || activity.getStatus() == null || activity.getStatus() != 1) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "预存活动不存在或已下架");
        }
        LocalDateTime now = LocalDateTime.now();
        if (activity.getStartTime() != null && now.isBefore(activity.getStartTime())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "预存活动尚未开始");
        }
        if (activity.getEndTime() != null && now.isAfter(activity.getEndTime())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "预存活动已结束");
        }
        Long tierCount = rechargeActivityTierMapper.selectCount(Wrappers.<RechargeActivityTier>lambdaQuery()
                .eq(RechargeActivityTier::getActivityId, activityId)
                .eq(RechargeActivityTier::getLevelId, level.getId()));
        if (tierCount == null || tierCount == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "该活动不适用所选档位");
        }
        if (activity.getLimitPerStore() != null && activity.getLimitPerStore() > 0) {
            Long used = rechargeOrderMapper.selectCount(Wrappers.<RechargeOrder>lambdaQuery()
                    .eq(RechargeOrder::getStoreId, store.getId())
                    .eq(RechargeOrder::getActivityId, activityId)
                    .eq(RechargeOrder::getStatus, 1));
            if (used != null && used >= activity.getLimitPerStore()) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "该活动参与次数已用完");
            }
        }
    }

    private void ensureNoActiveOrder(Long storeId, String action) {
        // 上次未支付就退出的挂单会卡住本次下单，先把「从未真正发起支付」的单作废
        cancelNeverPaid(storeId, action);
        Long count;
        if (WalletBizType.RECHARGE.name().equals(action)) {
            count = rechargeOrderMapper.selectCount(Wrappers.<RechargeOrder>lambdaQuery()
                    .eq(RechargeOrder::getStoreId, storeId)
                    .in(RechargeOrder::getStatus, STATUS_PENDING, STATUS_ABNORMAL));
        } else {
            count = joinOrderMapper.selectCount(Wrappers.<JoinOrder>lambdaQuery()
                    .eq(JoinOrder::getStoreId, storeId)
                    .in(JoinOrder::getStatus, STATUS_PENDING, STATUS_ABNORMAL));
        }
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "存在未完成的同类型单据，请先处理后再下单");
        }
    }

    /**
     * 作废从未真正支付的挂单：payment 从未进入支付中，或虽进入但扫呗侧没有交易号（未扫码）。
     * 已有交易号说明用户可能正在付款，保留不动作，交由超时关单或回调处理。
     */
    private void cancelNeverPaid(Long storeId, String action) {
        boolean recharge = WalletBizType.RECHARGE.name().equals(action);
        List<Long> bizIds = recharge
                ? rechargeOrderMapper.selectList(Wrappers.<RechargeOrder>lambdaQuery()
                        .eq(RechargeOrder::getStoreId, storeId)
                        .in(RechargeOrder::getStatus, STATUS_PENDING, STATUS_ABNORMAL))
                    .stream().map(RechargeOrder::getId).toList()
                : joinOrderMapper.selectList(Wrappers.<JoinOrder>lambdaQuery()
                        .eq(JoinOrder::getStoreId, storeId)
                        .in(JoinOrder::getStatus, STATUS_PENDING, STATUS_ABNORMAL))
                    .stream().map(JoinOrder::getId).toList();
        for (Long bizId : bizIds) {
            Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                    .eq(Payment::getBizId, bizId)
                    .in(Payment::getBizType, "JOIN", "UPGRADE", "RECHARGE")
                    .orderByDesc(Payment::getId)
                    .last("LIMIT 1"));
            if (payment != null && Objects.equals(payment.getStatus(), 2)) {
                continue;
            }
            boolean mayBePaying = payment != null
                    && Objects.equals(payment.getStatus(), PAY_STATUS_PAYING)
                    && StrUtil.isNotBlank(payment.getOutTradeNo());
            if (mayBePaying) {
                continue;
            }
            if (payment != null) {
                paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                        .eq(Payment::getId, payment.getId())
                        .in(Payment::getStatus, 0, PAY_STATUS_PAYING)
                        .set(Payment::getStatus, PAY_STATUS_CLOSED));
            }
            if (recharge) {
                rechargeOrderMapper.update(null, Wrappers.<RechargeOrder>lambdaUpdate()
                        .eq(RechargeOrder::getId, bizId)
                        .in(RechargeOrder::getStatus, STATUS_PENDING, STATUS_ABNORMAL)
                        .set(RechargeOrder::getStatus, STATUS_CANCELED));
            } else {
                joinOrderMapper.update(null, Wrappers.<JoinOrder>lambdaUpdate()
                        .eq(JoinOrder::getId, bizId)
                        .in(JoinOrder::getStatus, STATUS_PENDING, STATUS_ABNORMAL)
                        .set(JoinOrder::getStatus, STATUS_CANCELED));
            }
            log.info("自动作废未支付挂单：storeId={} action={} bizId={}", storeId, action, bizId);
        }
    }

    private CreatedOrder createOrder(Store store, StoreLevel level, PathDecision decision, Long activityId,
                                     int channel, int offlineMethod, LocalDateTime receivedAt,
                                     PaidContext context) {
        ensureNoActiveOrder(store.getId(), decision.action());
        String orderNo = newNo(WalletBizType.RECHARGE.name().equals(decision.action()) ? "R" : "J");
        Long bizId;
        try {
            if (WalletBizType.RECHARGE.name().equals(decision.action())) {
                RechargeOrder order = new RechargeOrder();
                order.setOrderNo(orderNo);
                order.setStoreId(store.getId());
                order.setActivityId(activityId == null ? 0L : activityId);
                order.setLevelId(level.getId());
                order.setAmount(decision.payAmount());
                order.setStatus(STATUS_PENDING);
                order.setResolveType(0);
                order.setResolveBy(0L);
                rechargeOrderMapper.insert(order);
                bizId = order.getId();
            } else {
                JoinOrder order = new JoinOrder();
                order.setOrderNo(orderNo);
                order.setStoreId(store.getId());
                order.setLevelId(level.getId());
                order.setType(WalletBizType.UPGRADE.name().equals(decision.action()) ? 2 : 1);
                order.setBeforeLevelId(store.getLevelId() == null ? 0L : store.getLevelId());
                order.setAmount(decision.payAmount());
                order.setStatus(STATUS_PENDING);
                order.setResolveType(0);
                order.setResolveBy(0L);
                joinOrderMapper.insert(order);
                bizId = order.getId();
            }
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "存在未完成的同类型单据，请先处理后再下单");
        }

        Payment payment = new Payment();
        payment.setPayNo(newNo("P"));
        payment.setBizType(decision.action());
        payment.setBizId(bizId);
        payment.setStoreId(store.getId());
        payment.setAmount(decision.payAmount());
        payment.setChannel(channel);
        payment.setOfflineMethod(offlineMethod);
        payment.setStatus(receivedAt == null ? 0 : 2);
        payment.setOutTradeNo("");
        payment.setPaidAt(receivedAt);
        payment.setRefundAmount(0L);
        payment.setRefundNo("");
        payment.setRefundBy(0L);
        payment.setExpireAt(receivedAt == null
                ? LocalDateTime.now().plusMinutes(orderTimeoutMinutes()) : null);
        try {
            paymentMapper.insert(payment);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "支付单创建失败，请重试");
        }

        int status = STATUS_PENDING;
        if (receivedAt != null) {
            applyPaid(payment.getId(), context);
            status = 1;
        } else if (context.agreement() != null) {
            rememberSignContext(payment.getPayNo(), context);
        }
        return new CreatedOrder(orderNo, payment.getPayNo(), decision.action(), level,
                store.getLevelId() == null ? 0L : store.getLevelId(),
                decision.payAmount(), status);
    }

    private void applyPaid(Long paymentId, PaidContext context) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null || payment.getStatus() == null || payment.getStatus() != 2) {
            return;
        }
        if (WalletBizType.RECHARGE.name().equals(payment.getBizType())) {
            applyRechargePaid(payment, context);
        } else if (WalletBizType.JOIN.name().equals(payment.getBizType())
                || WalletBizType.UPGRADE.name().equals(payment.getBizType())) {
            applyJoinPaid(payment, context);
        } else if ("ORDER".equals(payment.getBizType())) {
            orderService.handlePaid(payment.getId());
        }
    }

    private void applyJoinPaid(Payment payment, PaidContext context) {
        JoinOrder order = joinOrderMapper.selectById(payment.getBizId());
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "开通单不存在");
        }
        LocalDateTime paidAt = payment.getPaidAt() == null ? LocalDateTime.now() : payment.getPaidAt();
        int claimed = joinOrderMapper.update(null, Wrappers.<JoinOrder>lambdaUpdate()
                .eq(JoinOrder::getId, order.getId())
                .eq(JoinOrder::getStatus, STATUS_PENDING)
                .set(JoinOrder::getStatus, 1)
                .set(JoinOrder::getPayTime, paidAt));
        if (claimed == 0) {
            return;
        }
        Store store = storeMapper.selectById(order.getStoreId());
        if (store == null) {
            throw new BizException(ErrorCode.STORE_NOT_FOUND);
        }
        StoreLevel target = storeLevelService.requireLevel(order.getLevelId());

        Store patch = new Store();
        patch.setId(store.getId());
        patch.setLevelId(target.getId());
        patch.setLevelPaidAmount(target.getFee());
        if (store.getJoinTime() == null) {
            patch.setJoinTime(paidAt);
        }
        int upgradeDays = target.getUpgradeDays() == null ? DEFAULT_UPGRADE_DAYS : target.getUpgradeDays();
        patch.setUpgradeDeadline(paidAt.plusDays(upgradeDays));
        storeMapper.updateById(patch);

        insertLevelLog(store.getId(), order.getBeforeLevelId(), target.getId(),
                payment.getBizType(), order.getOrderNo(), order.getAmount());

        WalletBizType walletBiz = WalletBizType.UPGRADE.name().equals(payment.getBizType())
                ? WalletBizType.UPGRADE : WalletBizType.JOIN;
        walletService.creditDeposit(store.getId(), order.getAmount(), walletBiz, order.getOrderNo(),
                context.source(), context.proofImages(), context.operatorId(), context.remark());

        String title = WalletBizType.UPGRADE.name().equals(payment.getBizType()) ? "补差升档成功" : "开通成功";
        insertEvent(store.getId(), payment.getBizType(), order.getOrderNo(), title, order.getAmount(),
                context, Map.of("levelId", target.getId(), "beforeLevelId", order.getBeforeLevelId()));
        noticeService.notifyStore(store.getId(), title,
                title + "，档位：" + target.getName() + "，金额 " + yuan(order.getAmount()),
                "JOIN", order.getOrderNo(), NoticeService.SMS_JOIN_SUCCESS);
        signIfNeeded(store.getId(), payment.getPayNo(), payment.getChannel(), context);
        CommissionBizType commissionBiz = WalletBizType.UPGRADE.name().equals(payment.getBizType())
                ? CommissionBizType.UPGRADE : CommissionBizType.JOIN;
        commissionService.dispatch(commissionBiz, order.getOrderNo(), store.getId(), order.getAmount());
        PerformanceBizType performanceBiz = WalletBizType.UPGRADE.name().equals(payment.getBizType())
                ? PerformanceBizType.UPGRADE : PerformanceBizType.JOIN;
        performanceService.record(performanceBiz, order.getOrderNo(), store.getId(), order.getAmount(), paidAt);
    }

    private void applyRechargePaid(Payment payment, PaidContext context) {
        RechargeOrder order = rechargeOrderMapper.selectById(payment.getBizId());
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "预存单不存在");
        }
        LocalDateTime paidAt = payment.getPaidAt() == null ? LocalDateTime.now() : payment.getPaidAt();
        int claimed = rechargeOrderMapper.update(null, Wrappers.<RechargeOrder>lambdaUpdate()
                .eq(RechargeOrder::getId, order.getId())
                .eq(RechargeOrder::getStatus, STATUS_PENDING)
                .set(RechargeOrder::getStatus, 1)
                .set(RechargeOrder::getPayTime, paidAt));
        if (claimed == 0) {
            return;
        }
        Store store = storeMapper.selectById(order.getStoreId());
        if (store == null) {
            throw new BizException(ErrorCode.STORE_NOT_FOUND);
        }
        Long levelId = store.getLevelId() == null ? 0L : store.getLevelId();
        insertLevelLog(store.getId(), levelId, levelId,
                WalletBizType.RECHARGE.name(), order.getOrderNo(), order.getAmount());

        WalletBizType walletBiz = context.source() == WalletSource.OFFLINE
                ? WalletBizType.OFFLINE : WalletBizType.RECHARGE;
        walletService.creditDeposit(store.getId(), order.getAmount(), walletBiz, order.getOrderNo(),
                context.source(), context.proofImages(), context.operatorId(), context.remark());

        String title = context.source() == WalletSource.OFFLINE ? "线下收款预存" : "预存成功";
        String eventType = context.source() == WalletSource.OFFLINE
                ? WalletBizType.OFFLINE.name() : WalletBizType.RECHARGE.name();
        insertEvent(store.getId(), eventType, order.getOrderNo(), title, order.getAmount(),
                context, Map.of("levelId", order.getLevelId()));
        CommissionBizType commissionBiz = context.source() == WalletSource.OFFLINE
                ? CommissionBizType.OFFLINE : CommissionBizType.RECHARGE;
        commissionService.dispatch(commissionBiz, order.getOrderNo(), store.getId(), order.getAmount());
        PerformanceBizType performanceBiz = context.source() == WalletSource.OFFLINE
                ? PerformanceBizType.OFFLINE : PerformanceBizType.RECHARGE;
        performanceService.record(performanceBiz, order.getOrderNo(), store.getId(), order.getAmount(), paidAt);
        signIfNeeded(store.getId(), payment.getPayNo(), payment.getChannel(), context);
    }

    private void signIfNeeded(Long storeId, String payNo, Integer channel, PaidContext context) {
        if (context.signType() == 0) {
            return;
        }
        Agreement agreement = context.agreement();
        String ip = context.ip();
        if (channel != null && channel == CHANNEL_JSAPI) {
            String value = redisTemplate.opsForValue().getAndDelete(SIGN_KEY + payNo);
            if (StrUtil.isNotBlank(value)) {
                String[] parts = value.split("\\|", 3);
                try {
                    Agreement stored = agreementMapper.selectById(Long.parseLong(parts[0]));
                    if (stored != null) {
                        agreement = stored;
                    }
                } catch (NumberFormatException e) {
                    log.warn("协议签署上下文解析失败：payNo={} value={}", payNo, value);
                }
                if (parts.length > 2) {
                    ip = parts[2];
                }
            }
        }
        if (agreement == null) {
            agreement = agreementService.requireActive();
        }
        List<String> images = StrUtil.isBlank(context.agreementImages())
                ? null : JSONUtil.toList(context.agreementImages(), String.class);
        agreementService.sign(storeId, agreement, context.signType(), images, ip);
    }

    private void rememberSignContext(String payNo, PaidContext context) {
        try {
            String value = context.agreement().getId() + "|" + context.agreement().getVersion()
                    + "|" + StrUtil.nullToEmpty(context.ip());
            redisTemplate.opsForValue().set(SIGN_KEY + payNo, value, Duration.ofMinutes(30));
        } catch (Exception e) {
            log.warn("记录协议签署上下文失败：payNo={} error={}", payNo, e.toString());
        }
    }

    private void insertLevelLog(Long storeId, Long fromLevel, Long toLevel, String bizType,
                                String bizNo, Long amount) {
        StoreLevelLog log = new StoreLevelLog();
        log.setStoreId(storeId);
        log.setFromLevel(fromLevel == null ? 0L : fromLevel);
        log.setToLevel(toLevel);
        log.setBizType(bizType);
        log.setBizNo(bizNo);
        log.setAmount(amount);
        storeLevelLogMapper.insert(log);
    }

    private void insertEvent(Long storeId, String eventType, String bizNo, String title, Long amount,
                             PaidContext context, Map<String, Object> detail) {
        StoreEvent event = new StoreEvent();
        event.setStoreId(storeId);
        event.setEventType(eventType);
        event.setBizNo(bizNo);
        event.setTitle(title);
        event.setAmount(amount);
        event.setDirection(1);
        event.setAccount(1);
        event.setDetail(JSONUtil.toJsonStr(detail));
        event.setProofImages(context.proofImages());
        event.setOperatorType(context.operatorId() != null && context.operatorId() > 0 ? 2 : 1);
        event.setOperatorId(context.operatorId() == null ? 0L : context.operatorId());
        storeEventMapper.insert(event);
    }

    private void insertSystemEvent(Long storeId, String eventType, String bizNo, String title,
                                   Long amount, int direction, Map<String, Object> detail) {
        StoreEvent event = new StoreEvent();
        event.setStoreId(storeId);
        event.setEventType(eventType);
        event.setBizNo(bizNo);
        event.setTitle(title);
        event.setAmount(amount == null ? 0L : amount);
        event.setDirection(direction);
        event.setAccount(1);
        event.setDetail(JSONUtil.toJsonStr(detail));
        event.setOperatorType(3);
        event.setOperatorId(0L);
        storeEventMapper.insert(event);
    }

    private int orderTimeoutMinutes() {
        return Math.max(1, sysConfigService.getInt(SysConfigKeys.ORDER_TIMEOUT_MINUTES, 10));
    }

    private void validateOfflineMethod(Integer offlineMethod) {
        if (offlineMethod == null || offlineMethod < 1 || offlineMethod > 3) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "收款方式不正确");
        }
    }

    private String newNo(String prefix) {
        return prefix + NO_FORMAT.format(LocalDateTime.now()) + RandomUtil.randomNumbers(4);
    }

    private String yuan(long amount) {
        return java.math.BigDecimal.valueOf(amount, 2).toPlainString() + " 元";
    }

    private String jsonArray(List<String> values) {
        return values == null || values.isEmpty() ? null : JSONUtil.toJsonStr(values);
    }

    private PurchaseOrderVO toVO(CreatedOrder created) {
        return new PurchaseOrderVO(created.orderNo(), created.payNo(), created.bizType(),
                created.level().getId(), created.level().getName(), created.beforeLevelId(),
                created.amount(), created.status());
    }

    private record PathDecision(String action, long payAmount, StoreLevel level) {
    }

    private record CreatedOrder(String orderNo, String payNo, String bizType, StoreLevel level,
                                Long beforeLevelId, long amount, int status) {
    }

    private record PaidContext(WalletSource source, String proofImages, Long operatorId, String remark,
                               int signType, String agreementImages, String ip, Agreement agreement) {
        static PaidContext online() {
            return new PaidContext(WalletSource.ONLINE, null, 0L, "线上购买",
                    SIGN_ONLINE, null, "", null);
        }
    }
}

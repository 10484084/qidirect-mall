package com.qidirect.mall.infra.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.entity.OrderCancelRequest;
import com.qidirect.mall.domain.entity.OrderInfo;
import com.qidirect.mall.domain.entity.Payment;
import com.qidirect.mall.domain.vo.OrderCancelVO;
import com.qidirect.mall.infra.mapper.OrderCancelRequestMapper;
import com.qidirect.mall.infra.mapper.OrderInfoMapper;
import com.qidirect.mall.infra.mapper.PaymentMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 订单取消申请：门店在厂家发货前申请，厂家审核通过后按支付方式退款
 * （预存部分退回余额，在线支付部分走扫呗原路退回）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCancelService {

    private static final int ORDER_PAID = 1;
    private static final int ORDER_CANCELED = 4;
    private static final int PAY_SUCCESS = 2;
    private static final int PAY_REFUNDED = 5;

    private final OrderCancelRequestMapper cancelMapper;
    private final OrderInfoMapper orderInfoMapper;
    private final PaymentMapper paymentMapper;
    private final WalletService walletService;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactionTemplate;

    /**
     * 门店提交取消申请：仅「已支付且未发货」的订单可申请，且同一订单不能有进行中的申请。
     */
    public OrderCancelVO apply(Long storeId, Long orderId, String reason) {
        OrderInfo order = orderInfoMapper.selectById(orderId);
        if (order == null || !Objects.equals(order.getStoreId(), storeId)) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getPayTime() == null || !order.getPayTime().toLocalDate().equals(LocalDate.now())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "仅支持支付当天申请取消退款，逾期请联系厂家处理");
        }
        if (!Objects.equals(order.getStatus(), ORDER_PAID)) {
            if (Objects.equals(order.getStatus(), 0)) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "订单未支付，可直接取消，无需申请");
            }
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "订单已发货或已结束，无法申请取消");
        }
        Long active = cancelMapper.selectCount(Wrappers.<OrderCancelRequest>lambdaQuery()
                .eq(OrderCancelRequest::getOrderId, orderId)
                .in(OrderCancelRequest::getStatus,
                        OrderCancelRequest.STATUS_PENDING,
                        OrderCancelRequest.STATUS_PROCESSING,
                        OrderCancelRequest.STATUS_REFUND_FAILED));
        if (active != null && active > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "该订单已有取消申请，请等待厂家处理");
        }
        OrderCancelRequest request = new OrderCancelRequest();
        request.setOrderId(orderId);
        request.setOrderNo(order.getOrderNo());
        request.setStoreId(storeId);
        request.setReason(StrUtil.nullToEmpty(reason).trim());
        request.setStatus(OrderCancelRequest.STATUS_PENDING);
        request.setDepositRefund(0L);
        request.setOnlineRefund(0L);
        request.setRefundNo("");
        request.setRefundMsg("");
        request.setAuditBy(0L);
        request.setAuditRemark("");
        cancelMapper.insert(request);
        log.info("门店提交取消申请：orderNo={} storeId={} reason={}", order.getOrderNo(), storeId, reason);
        return toVO(request);
    }

    public PageResult<OrderCancelVO> page(Integer status, long page, long size) {
        Page<OrderCancelRequest> result = cancelMapper.selectPage(new Page<>(page, size),
                Wrappers.<OrderCancelRequest>lambdaQuery()
                        .eq(status != null, OrderCancelRequest::getStatus, status)
                        .orderByDesc(OrderCancelRequest::getId));
        List<OrderCancelVO> records = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    /**
     * 审核：驳回直接置状态；通过则先占位为处理中，事务外调扫呗退款，成功后取消订单并退预存。
     */
    public OrderCancelVO audit(Long cancelId, boolean approved, String remark, Long adminId) {
        OrderCancelRequest request = cancelMapper.selectById(cancelId);
        if (request == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "取消申请不存在");
        }
        if (!approved) {
            transactionTemplate.executeWithoutResult(s -> {
                OrderCancelRequest locked = cancelMapper.selectById(cancelId);
                if (locked == null || !Objects.equals(locked.getStatus(), OrderCancelRequest.STATUS_PENDING)) {
                    throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "该申请已处理");
                }
                locked.setStatus(OrderCancelRequest.STATUS_REJECTED);
                locked.setAuditBy(adminId);
                locked.setAuditAt(LocalDateTime.now());
                locked.setAuditRemark(StrUtil.nullToEmpty(remark));
                cancelMapper.updateById(locked);
            });
            return toVO(cancelMapper.selectById(cancelId));
        }

        // 第一阶段：锁定申请与订单，占位为处理中，避免重复退款
        OrderInfo order = transactionTemplate.execute(s -> {
            OrderCancelRequest locked = cancelMapper.selectById(cancelId);
            if (locked == null || !Objects.equals(locked.getStatus(), OrderCancelRequest.STATUS_PENDING)) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "该申请已处理");
            }
            OrderInfo lockedOrder = orderInfoMapper.selectById(locked.getOrderId());
            if (lockedOrder == null || !Objects.equals(lockedOrder.getStatus(), ORDER_PAID)) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "订单状态已变化，无法取消");
            }
            locked.setStatus(OrderCancelRequest.STATUS_PROCESSING);
            locked.setAuditBy(adminId);
            locked.setAuditAt(LocalDateTime.now());
            locked.setAuditRemark(StrUtil.nullToEmpty(remark));
            cancelMapper.updateById(locked);
            return lockedOrder;
        });
        if (order == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "订单不存在");
        }

        long onlineAmount = order.getOnlineAmount() == null ? 0L : order.getOnlineAmount();
        long depositAmount = order.getDepositAmount() == null ? 0L : order.getDepositAmount();
        String refundNo = "";
        String refundMsg = "";

        // 第二阶段：事务外原路退款
        if (onlineAmount > 0) {
            Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                    .eq(Payment::getBizType, "ORDER")
                    .eq(Payment::getBizId, order.getId())
                    .eq(Payment::getStatus, PAY_SUCCESS)
                    .orderByDesc(Payment::getId)
                    .last("LIMIT 1"));
            if (payment == null) {
                failRefund(cancelId, "未找到成功的在线支付单，无法原路退款");
                return toVO(cancelMapper.selectById(cancelId));
            }
            String outRefundNo = "RF" + order.getOrderNo();
            PaymentGateway.RefundResult result;
            try {
                result = paymentGateway.refund(payment, onlineAmount, outRefundNo);
            } catch (Exception e) {
                log.error("原路退款调用异常：orderNo={} error={}", order.getOrderNo(), e.toString());
                failRefund(cancelId, "退款接口异常：" + e.getMessage());
                return toVO(cancelMapper.selectById(cancelId));
            }
            if (!result.success()) {
                failRefund(cancelId, StrUtil.blankToDefault(result.message(), "退款失败"));
                return toVO(cancelMapper.selectById(cancelId));
            }
            refundNo = StrUtil.nullToEmpty(result.refundNo());
            refundMsg = StrUtil.nullToEmpty(result.message());
        }

        // 第三阶段：落库（订单取消 + 退预存 + 申请单完成）
        final String finalRefundNo = refundNo;
        final String finalRefundMsg = refundMsg;
        transactionTemplate.executeWithoutResult(s -> {
            OrderCancelRequest locked = cancelMapper.selectById(cancelId);
            if (locked == null || !Objects.equals(locked.getStatus(), OrderCancelRequest.STATUS_PROCESSING)) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "申请状态异常");
            }
            int claimed = orderInfoMapper.update(null, Wrappers.<OrderInfo>lambdaUpdate()
                    .eq(OrderInfo::getId, order.getId())
                    .eq(OrderInfo::getStatus, ORDER_PAID)
                    .set(OrderInfo::getStatus, ORDER_CANCELED));
            if (claimed == 0) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "订单状态已变化，无法取消");
            }
            if (depositAmount > 0) {
                walletService.refundDepositForOrder(order.getStoreId(), depositAmount, order.getOrderNo());
            }
            if (onlineAmount > 0) {
                paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                        .eq(Payment::getBizType, "ORDER")
                        .eq(Payment::getBizId, order.getId())
                        .eq(Payment::getStatus, PAY_SUCCESS)
                        .set(Payment::getStatus, PAY_REFUNDED)
                        .set(Payment::getRefundAmount, onlineAmount)
                        .set(Payment::getRefundNo, finalRefundNo)
                        .set(Payment::getRefundBy, adminId)
                        .set(Payment::getRefundAt, LocalDateTime.now()));
            }
            locked.setStatus(OrderCancelRequest.STATUS_APPROVED);
            locked.setDepositRefund(depositAmount);
            locked.setOnlineRefund(onlineAmount);
            locked.setRefundNo(finalRefundNo);
            locked.setRefundMsg(finalRefundMsg);
            cancelMapper.updateById(locked);
        });
        log.info("订单取消退款完成：orderNo={} 预存退回={} 原路退回={}",
                order.getOrderNo(), depositAmount, onlineAmount);
        return toVO(cancelMapper.selectById(cancelId));
    }

    private void failRefund(Long cancelId, String message) {
        transactionTemplate.executeWithoutResult(s -> {
            OrderCancelRequest locked = cancelMapper.selectById(cancelId);
            if (locked == null) {
                return;
            }
            locked.setStatus(OrderCancelRequest.STATUS_REFUND_FAILED);
            locked.setRefundMsg(StrUtil.maxLength(message, 240));
            cancelMapper.updateById(locked);
        });
    }

    /**
     * 门店侧查询自己订单的申请情况。
     */
    public OrderCancelVO latestOfOrder(Long storeId, Long orderId) {
        OrderCancelRequest request = cancelMapper.selectOne(Wrappers.<OrderCancelRequest>lambdaQuery()
                .eq(OrderCancelRequest::getOrderId, orderId)
                .eq(OrderCancelRequest::getStoreId, storeId)
                .orderByDesc(OrderCancelRequest::getId)
                .last("LIMIT 1"));
        return request == null ? null : toVO(request);
    }

    private OrderCancelVO toVO(OrderCancelRequest r) {
        return new OrderCancelVO(r.getId(), r.getOrderId(), r.getOrderNo(), r.getStoreId(),
                r.getReason(), r.getStatus(), r.getDepositRefund(), r.getOnlineRefund(),
                r.getRefundNo(), r.getRefundMsg(), r.getAuditRemark(), r.getAuditAt(), r.getCreatedAt());
    }
}

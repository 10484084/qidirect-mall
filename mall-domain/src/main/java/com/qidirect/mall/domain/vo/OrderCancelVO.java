package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;

/**
 * 订单取消申请视图。
 */
public record OrderCancelVO(
        Long id,
        Long orderId,
        String orderNo,
        Long storeId,
        String reason,
        Integer status,
        Long depositRefund,
        Long onlineRefund,
        String refundNo,
        String refundMsg,
        String auditRemark,
        LocalDateTime auditAt,
        LocalDateTime createdAt) {
}

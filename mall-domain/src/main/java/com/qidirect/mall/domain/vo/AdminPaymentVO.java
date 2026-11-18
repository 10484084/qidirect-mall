package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;

/**
 * 后台财务：支付流水行。
 */
public record AdminPaymentVO(
        Long id,
        String payNo,
        String bizType,
        Long bizId,
        Long storeId,
        String storeName,
        Long amount,
        Integer channel,
        Integer offlineMethod,
        Integer status,
        String outTradeNo,
        LocalDateTime paidAt,
        Long refundAmount,
        String refundNo,
        Long refundBy,
        LocalDateTime createdAt) {
}

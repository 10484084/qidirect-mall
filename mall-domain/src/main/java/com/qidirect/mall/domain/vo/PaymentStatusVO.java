package com.qidirect.mall.domain.vo;

/**
 * 支付单状态，供前端轮询。
 */
public record PaymentStatusVO(
        String payNo,
        Integer status,
        boolean paid) {
}

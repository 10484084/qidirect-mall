package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;

/**
 * 后台财务：支付回调原始报文（仅后台可查，不进入门店时间线）。
 */
public record AdminPaymentNotifyVO(
        Long id,
        String payNo,
        String rawBody,
        Integer verified,
        Integer handled,
        LocalDateTime createdAt) {
}

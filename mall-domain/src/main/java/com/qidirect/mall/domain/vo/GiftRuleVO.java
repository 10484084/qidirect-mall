package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;

/**
 * 买赠规则。
 */
public record GiftRuleVO(
        Long id,
        Long productId,
        Long giftSkuId,
        String giftSkuName,
        Integer thresholdQty,
        Integer giftQty,
        Integer enabled,
        LocalDateTime startTime,
        LocalDateTime endTime) {
}

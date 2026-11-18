package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 买赠规则新增/修改。启用时同事务先停用该商品旧规则，保证任一时刻仅一条 enabled=1。
 */
public record GiftRuleSaveRequest(
        @NotNull Long productId,
        @NotNull Long giftSkuId,
        @NotNull @Min(1) Integer thresholdQty,
        @NotNull @Min(1) Integer giftQty,
        Integer enabled,
        LocalDateTime startTime,
        LocalDateTime endTime) {
}

package com.qidirect.mall.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 业绩奖记录。
 */
public record PerformanceBonusVO(
        Long id,
        Long storeId,
        String storeName,
        String month,
        Long performance,
        Long ruleId,
        BigDecimal rate,
        Long bonusAmount,
        Integer status,
        Long revisionOf,
        LocalDateTime settledAt,
        LocalDateTime createdAt) {
}

package com.qidirect.mall.domain.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 门店业绩页：月度业绩、阶梯进度、调整明细与奖励记录。
 */
public record StorePerformanceVO(
        String month,
        String levelName,
        Boolean bonusEnabled,
        Long teamPerformance,
        Long ownPerformance,
        Long currentMinAmount,
        BigDecimal currentRate,
        Long nextMinAmount,
        Long gapToNext,
        Long expectedBonus,
        List<PerformanceTierVO> tiers,
        List<PerformanceRecordVO> adjustments,
        List<PerformanceBonusVO> bonuses) {
}

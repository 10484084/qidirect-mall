package com.qidirect.mall.domain.vo;

import java.math.BigDecimal;

/**
 * 业绩奖阶梯规则。
 */
public record PerformanceRuleVO(
        Long id,
        Long minAmount,
        BigDecimal rate,
        Integer sort,
        Integer status) {
}

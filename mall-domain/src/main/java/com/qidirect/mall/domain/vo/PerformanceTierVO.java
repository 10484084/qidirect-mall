package com.qidirect.mall.domain.vo;

import java.math.BigDecimal;

/**
 * 门店端阶梯进度：当前业绩命中的档位与距离下一档还差的业绩。
 */
public record PerformanceTierVO(
        Long ruleId,
        Long minAmount,
        BigDecimal rate,
        Boolean reached,
        Long gapToNext) {
}

package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 结算后业绩奖冲正：correctedPerformance 为修正后的当月业绩（分）。
 */
public record PerformanceReversalRequest(
        @NotNull @Min(0) Long correctedPerformance,
        String remark) {
}

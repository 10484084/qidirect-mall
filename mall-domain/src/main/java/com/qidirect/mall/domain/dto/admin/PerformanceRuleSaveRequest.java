package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 业绩奖阶梯规则保存：minAmount 为业绩门槛（分），rate 以小数表示。
 */
public record PerformanceRuleSaveRequest(
        @NotNull @Min(0) Long minAmount,
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal rate,
        Integer sort,
        Integer status) {
}

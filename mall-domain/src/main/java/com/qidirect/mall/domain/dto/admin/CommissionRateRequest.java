package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 提成比例配置：以小数表示，如 0.10 表示 10%。
 */
public record CommissionRateRequest(
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal rate1,
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal rate2) {
}

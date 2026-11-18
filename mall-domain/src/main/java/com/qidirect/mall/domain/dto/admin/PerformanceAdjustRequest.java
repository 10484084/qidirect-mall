package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 结算前业绩修正：只新增一条 ADJUST 业绩流水，不改原流水。
 */
public record PerformanceAdjustRequest(
        @NotNull Long storeId,
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}", message = "月份格式须为 yyyy-MM") String month,
        @NotNull Long amount,
        @NotBlank String remark) {
}

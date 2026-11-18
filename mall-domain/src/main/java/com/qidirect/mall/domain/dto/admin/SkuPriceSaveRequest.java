package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 规格档位价。price 单位为分。
 */
public record SkuPriceSaveRequest(
        @NotNull Long levelId,
        @NotNull @Min(0) Long price) {
}

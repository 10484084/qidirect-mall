package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 档位新增/修改。非最高 sort 档位禁止开启长期提成、业绩奖、买赠。
 */
public record LevelSaveRequest(
        @NotBlank String name,
        @NotNull @Min(0) Long fee,
        @NotNull Integer sort,
        Integer upgradeDays,
        Integer longTerm,
        Integer hasBonus,
        Integer hasGift) {
}

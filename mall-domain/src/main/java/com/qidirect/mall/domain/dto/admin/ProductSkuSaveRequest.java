package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 商品规格。id 为空表示新增，有值表示更新；未提交的已有规格会被删除（被引用时拒绝）。
 */
public record ProductSkuSaveRequest(
        Long id,
        @NotBlank String specName,
        @NotNull @Min(1) Integer factor,
        Integer sort,
        List<@Valid SkuPriceSaveRequest> prices) {
}

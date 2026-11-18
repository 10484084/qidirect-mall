package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 商品新增/修改，规格与三档价一并提交。
 */
public record ProductSaveRequest(
        @NotNull Long categoryId,
        @NotBlank String name,
        String cover,
        List<String> images,
        String description,
        Integer v3GiftEnabled,
        Integer recommend,
        Integer status,
        Integer sort,
        @NotEmpty List<@Valid ProductSkuSaveRequest> skus) {
}

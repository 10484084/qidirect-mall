package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotBlank;

/**
 * 商品分类新增/修改。
 */
public record ProductCategorySaveRequest(
        @NotBlank String name,
        Integer sort,
        Integer status) {
}

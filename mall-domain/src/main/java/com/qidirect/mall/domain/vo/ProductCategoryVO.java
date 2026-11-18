package com.qidirect.mall.domain.vo;

/**
 * 商品分类。
 */
public record ProductCategoryVO(
        Long id,
        String name,
        Integer sort,
        Integer status) {
}

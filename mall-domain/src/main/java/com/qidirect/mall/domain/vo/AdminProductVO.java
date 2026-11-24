package com.qidirect.mall.domain.vo;

import java.util.List;

/**
 * 后台商品视图。
 */
public record AdminProductVO(
        Long id,
        Long categoryId,
        String name,
        String unit,
        String cover,
        List<String> images,
        String description,
        Integer v3GiftEnabled,
        Integer recommend,
        Integer status,
        Integer sort,
        List<AdminSkuVO> skus) {
}

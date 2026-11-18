package com.qidirect.mall.domain.vo;

import java.util.List;

/**
 * 门店端商品视图。price 为当前档位下所有可购规格的最低价（无可用规格时为空）。
 */
public record StoreProductVO(
        Long id,
        Long categoryId,
        String name,
        String cover,
        List<String> images,
        String description,
        Integer v3GiftEnabled,
        Integer recommend,
        Integer status,
        Integer sort,
        Long levelId,
        Long minPrice,
        boolean purchasable,
        List<StoreSkuVO> skus,
        GiftRuleVO giftRule) {
}

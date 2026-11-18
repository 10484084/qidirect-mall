package com.qidirect.mall.domain.vo;

/**
 * 门店端规格视图：按当前门店档位返回单价，缺价时 purchasable=false（前台置灰）。
 */
public record StoreSkuVO(
        Long id,
        String specName,
        Integer factor,
        Integer sort,
        Long price,
        boolean purchasable) {
}

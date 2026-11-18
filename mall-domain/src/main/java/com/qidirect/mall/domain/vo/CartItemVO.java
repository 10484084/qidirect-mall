package com.qidirect.mall.domain.vo;

/**
 * 购物车行。unitPrice 为空表示当前档位缺价，不可结算。
 */
public record CartItemVO(
        Long id,
        Long productId,
        String productName,
        String cover,
        Long skuId,
        String specName,
        Integer factor,
        Integer qty,
        Long unitPrice,
        Long amount,
        boolean purchasable,
        Integer productStatus) {
}

package com.qidirect.mall.domain.vo;

/**
 * 订单明细展示。amount 为 price × qty，赠品恒为 0。
 */
public record OrderItemVO(
        Long productId,
        String productName,
        Long skuId,
        String specName,
        Long price,
        Integer qty,
        Integer isGift,
        Long amount) {
}

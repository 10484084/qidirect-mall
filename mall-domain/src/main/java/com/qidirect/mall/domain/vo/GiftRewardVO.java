package com.qidirect.mall.domain.vo;

/**
 * 结算赠品行：0 元同商品瓶数，随主货发出，不计金额/业绩/提成。
 */
public record GiftRewardVO(
        Long productId,
        Long skuId,
        String specName,
        Integer qty,
        Long price) {
}

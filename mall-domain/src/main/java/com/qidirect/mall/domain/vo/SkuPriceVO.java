package com.qidirect.mall.domain.vo;

/**
 * 规格在某个档位下的价格。
 */
public record SkuPriceVO(
        Long levelId,
        Long price) {
}

package com.qidirect.mall.domain.vo;

import java.util.List;

/**
 * 后台商品规格视图，含全部档位价。
 */
public record AdminSkuVO(
        Long id,
        String specName,
        Integer factor,
        Integer sort,
        List<SkuPriceVO> prices) {
}

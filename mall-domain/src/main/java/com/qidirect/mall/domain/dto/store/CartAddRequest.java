package com.qidirect.mall.domain.dto.store;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 加入购物车。同一规格重复加入会累加数量。
 */
public record CartAddRequest(
        @NotNull Long skuId,
        @NotNull @Min(1) Integer qty) {
}

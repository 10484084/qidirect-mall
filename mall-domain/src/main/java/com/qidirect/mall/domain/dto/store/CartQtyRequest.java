package com.qidirect.mall.domain.dto.store;

import jakarta.validation.constraints.NotNull;

/**
 * 修改购物车数量，qty <= 0 表示删除该行。
 */
public record CartQtyRequest(
        @NotNull Integer qty) {
}

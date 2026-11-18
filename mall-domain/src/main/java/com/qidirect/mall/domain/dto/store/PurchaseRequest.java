package com.qidirect.mall.domain.dto.store;

import jakarta.validation.constraints.NotNull;

/**
 * 统一购买下单：档位 + 可选预存活动 + 协议勾选。
 */
public record PurchaseRequest(
        @NotNull Long levelId,
        Long activityId,
        @NotNull Boolean agreed) {
}

package com.qidirect.mall.domain.dto.store;

import jakarta.validation.constraints.Size;

/**
 * 门店申请取消订单。
 */
public record OrderCancelApplyRequest(
        @Size(max = 200, message = "取消原因不能超过 200 字")
        String reason) {
}

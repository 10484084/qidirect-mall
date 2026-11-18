package com.qidirect.mall.domain.dto.store;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 购物车结算下单。默认尽量使用预存余额，剩余金额生成在线支付单。
 */
public record OrderCreateRequest(
        @NotEmpty(message = "请选择结算商品") List<Long> cartItemIds,
        @NotBlank @Size(max = 32) String receiverName,
        @NotBlank @Size(max = 20) String receiverPhone,
        @NotBlank @Size(max = 255) String receiverAddr,
        Boolean useDeposit) {

    public boolean depositEnabled() {
        return useDeposit == null || useDeposit;
    }
}

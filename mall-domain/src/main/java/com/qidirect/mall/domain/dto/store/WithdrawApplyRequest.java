package com.qidirect.mall.domain.dto.store;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 提现申请：只填金额，收款卡取门店当前绑定卡快照。
 */
public record WithdrawApplyRequest(
        @NotNull(message = "提现金额不能为空")
        @Min(value = 1, message = "提现金额必须大于 0")
        Long amount) {
}

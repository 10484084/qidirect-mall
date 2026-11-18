package com.qidirect.mall.domain.vo;

/**
 * 单个门店的对账差额：钱包余额与流水净额（或冻结金额与在途提现）不一致时返回。
 */
public record ReconcileMismatchVO(
        Long storeId,
        long walletValue,
        long flowValue,
        long diff) {
}

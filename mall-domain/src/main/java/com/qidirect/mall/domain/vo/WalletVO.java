package com.qidirect.mall.domain.vo;

/**
 * 门店钱包概览。可用佣金 = 佣金余额 - 提现冻结金额。
 */
public record WalletVO(
        Long storeId,
        long depositBalance,
        long commissionBalance,
        long frozenAmount,
        long availableCommission) {
}

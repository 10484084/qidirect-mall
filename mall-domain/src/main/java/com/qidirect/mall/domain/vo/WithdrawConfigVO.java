package com.qidirect.mall.domain.vo;

import java.time.LocalDate;

/**
 * 门店端提现页配置：是否开放、最低额、可用余额、下次开放日与收款卡。
 */
public record WithdrawConfigVO(
        boolean open,
        LocalDate today,
        String openDays,
        Long minAmount,
        Long commissionBalance,
        Long frozenAmount,
        Long availableAmount,
        LocalDate nextOpenDate,
        boolean bankBound,
        String bankHolder,
        String bankName,
        String bankAccountMasked,
        /** 完整卡号，仅本人可见的提现配置接口返回 */
        String bankAccount) {
}

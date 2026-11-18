package com.qidirect.mall.common.enums;

import lombok.Getter;

/**
 * 钱包子账户：预存只能订货，佣金只能提现。
 */
@Getter
public enum WalletAccount {

    DEPOSIT(1),
    COMMISSION(2);

    private final int code;

    WalletAccount(int code) {
        this.code = code;
    }

    public static WalletAccount of(Integer code) {
        if (code == null) {
            return null;
        }
        for (WalletAccount account : values()) {
            if (account.code == code) {
                return account;
            }
        }
        return null;
    }
}

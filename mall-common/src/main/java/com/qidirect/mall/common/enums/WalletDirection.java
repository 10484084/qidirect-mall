package com.qidirect.mall.common.enums;

import lombok.Getter;

/**
 * 余额变动方向：1 入账，-1 出账。
 */
@Getter
public enum WalletDirection {

    IN(1),
    OUT(-1);

    private final int code;

    WalletDirection(int code) {
        this.code = code;
    }
}

package com.qidirect.mall.common.enums;

import lombok.Getter;

/**
 * 余额变动来源：1 线上，2 线下，3 人工。
 */
@Getter
public enum WalletSource {

    ONLINE(1),
    OFFLINE(2),
    MANUAL(3);

    private final int code;

    WalletSource(int code) {
        this.code = code;
    }
}

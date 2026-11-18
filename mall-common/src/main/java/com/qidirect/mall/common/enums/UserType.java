package com.qidirect.mall.common.enums;

import com.qidirect.mall.common.constant.AuthConstants;
import lombok.Getter;

/**
 * 登录主体类型：门店端 / 厂家端。JWT 中 userType 与此对应，双端互不通用。
 */
@Getter
public enum UserType {

    ADMIN(AuthConstants.USER_TYPE_ADMIN),
    STORE(AuthConstants.USER_TYPE_STORE);

    private final String code;

    UserType(String code) {
        this.code = code;
    }
}

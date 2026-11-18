package com.qidirect.mall.common.constant;

/**
 * 鉴权相关常量：Header、JWT Claim 名称、双端标识。
 */
public final class AuthConstants {

    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    public static final String CLAIM_USER_TYPE = "userType";
    public static final String CLAIM_STORE_ID = "storeId";
    public static final String CLAIM_LEVEL_ID = "levelId";
    public static final String CLAIM_PERMS = "perms";

    public static final String USER_TYPE_ADMIN = "admin";
    public static final String USER_TYPE_STORE = "store";

    private AuthConstants() {
    }
}

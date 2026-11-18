package com.qidirect.mall.infra.security;

/**
 * 登录主体：门店端为 storeId/levelId，厂家端为 adminId。
 */
public record LoginUser(String userType, Long userId, Long storeId, Long levelId) {
}

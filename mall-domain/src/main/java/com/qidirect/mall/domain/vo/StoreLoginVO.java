package com.qidirect.mall.domain.vo;

/**
 * 门店端登录/注册返回。
 */
public record StoreLoginVO(
        String token,
        Long storeId,
        String phone,
        String nickname,
        Long levelId,
        Integer authStatus,
        String inviteCode) {

    public boolean joined() {
        return levelId != null && levelId > 0;
    }
}

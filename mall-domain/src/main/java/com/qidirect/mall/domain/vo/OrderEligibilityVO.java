package com.qidirect.mall.domain.vo;

/**
 * 下单资格。未实名或未加盟均不可下单。
 */
public record OrderEligibilityVO(
        boolean canOrder,
        Integer authStatus,
        boolean joined,
        boolean frozen,
        String reason) {
}

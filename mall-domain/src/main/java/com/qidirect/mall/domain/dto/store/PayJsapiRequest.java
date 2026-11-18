package com.qidirect.mall.domain.dto.store;

/**
 * 公众号支付授权回跳后，bridge 页携带的微信授权 code。
 */
public record PayJsapiRequest(String code) {
}

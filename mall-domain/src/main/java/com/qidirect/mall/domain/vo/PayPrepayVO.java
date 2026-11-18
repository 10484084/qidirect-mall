package com.qidirect.mall.domain.vo;

import java.util.Map;

/**
 * 支付调起信息：mock 模式直接返回 payUrl，真实公众号支付返回授权地址或 JSAPI 参数。
 */
public record PayPrepayVO(
        String payNo,
        Integer status,
        boolean paid,
        String authUrl,
        String payUrl,
        Map<String, String> payParams) {
}

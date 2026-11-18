package com.qidirect.mall.infra.service;

import com.qidirect.mall.domain.entity.Payment;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 支付网关抽象：JSAPI 下单、关单、查单与回调验签。外部请求必须在数据库事务之外调用。
 */
public interface PaymentGateway {

    /**
     * 4.3 公众号 JSAPI 下单。返回收银台地址或可直接调起的 JSAPI 参数。
     */
    JsapiResult jsapi(Payment payment, String openId);

    /** 4.8 关单 */
    void close(Payment payment);

    /** 4.5 查单 */
    PaymentQueryResult query(Payment payment);

    /** 4.10 回调验签 */
    boolean verifyNotify(Map<String, String> params);

    record JsapiResult(String payUrl, Map<String, String> payParams, String outTradeNo) {
    }

    record PaymentQueryResult(boolean paid, String outTradeNo, LocalDateTime paidAt) {
    }
}

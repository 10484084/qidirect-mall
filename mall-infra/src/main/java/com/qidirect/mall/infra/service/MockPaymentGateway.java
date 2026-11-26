package com.qidirect.mall.infra.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.domain.entity.Payment;
import com.qidirect.mall.infra.mapper.PaymentMapper;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 开发联调 Mock 网关：下单直接返回 Mock 支付地址，关单直接关闭本地待支付单，查单以本地支付结果为准。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    private final PaymentMapper paymentMapper;

    @Override
    public JsapiResult jsapi(Payment payment, String openId) {
        return new JsapiResult("/api/notify/mock/pay/" + payment.getPayNo(), Map.of(), "");
    }

    @Override
    public String cashierUrl(Payment payment) {
        return "/api/notify/mock/pay/" + payment.getPayNo();
    }

    @Override
    public void close(Payment payment) {
        paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                .eq(Payment::getId, payment.getId())
                .in(Payment::getStatus, 0, 1, 3)
                .set(Payment::getStatus, 4));
    }

    @Override
    public PaymentQueryResult query(Payment payment) {
        Payment current = paymentMapper.selectById(payment.getId());
        if (current == null || current.getStatus() == null || current.getStatus() != 2) {
            return new PaymentQueryResult(false, "", null);
        }
        return new PaymentQueryResult(true, current.getOutTradeNo(), current.getPaidAt());
    }

    @Override
    public boolean verifyNotify(Map<String, String> params) {
        return true;
    }

    @Override
    public RefundResult refund(Payment payment, long refundFee, String outRefundNo) {
        log.info("模拟退款：payNo={} fee={} outRefundNo={}", payment.getPayNo(), refundFee, outRefundNo);
        return new RefundResult(true, StrUtil.blankToDefault(outRefundNo, "MOCK" + System.currentTimeMillis()), "模拟退款成功");
    }
}

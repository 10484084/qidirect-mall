package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.entity.Payment;
import com.qidirect.mall.infra.config.SaobeiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 扫呗支付网关：4.3 公众号 JSAPI、4.5 查单、4.8 关单、4.10 回调验签。
 * 所有方法均执行外部 HTTP 请求，调用方必须在数据库事务之外调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.provider", havingValue = "saobei")
public class SaobeiPayGateway implements PaymentGateway {

    private static final String SUCCESS = "01";
    private static final String TRADE_SUCCESS = "SUCCESS";
    private static final int TIMEOUT_MS = 10_000;
    private static final DateTimeFormatter TERMINAL_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SaobeiProperties properties;

    @Override
    public JsapiResult jsapi(Payment payment, String openId) {
        requireConfigured();
        Map<String, String> params = baseParams(payment);
        params.put("open_id", openId);
        params.put("notify_url", properties.getNotifyUrl());
        params.put("total_fee", String.valueOf(payment.getAmount()));
        Map<String, String> response = post(properties.getJsapiPath(), params);
        requireSuccess(response, "公众号支付下单");
        return new JsapiResult(StrUtil.nullToEmpty(response.get("pay_url")),
                extractPayParams(response), StrUtil.nullToEmpty(response.get("out_trade_no")));
    }

    /**
     * 4.11 聚合码下单：pay_type=000 自动识别微信/支付宝，auto_pay=1 扫码直接拉起支付，
     * repeated_trace=3 同一流水号+时间只允许成功一次（失败可重试）。
     * 返回收银台链接 qr_url，由调用方在微信/支付宝内直接打开。
     */
    @Override
    public String cashierUrl(Payment payment) {
        requireConfigured();
        Map<String, String> params = baseParams(payment);
        params.put("pay_type", "000");
        params.put("service_id", "016");
        params.put("total_fee", String.valueOf(payment.getAmount()));
        params.put("auto_pay", "1");
        params.put("repeated_trace", "3");
        if (StrUtil.isNotBlank(properties.getNotifyUrl())) {
            params.put("notify_url", properties.getNotifyUrl());
        }
        String body = sanitizeOrderBody(payment);
        if (StrUtil.isNotBlank(body)) {
            params.put("order_body", body);
        }
        Map<String, String> response = post(properties.getQrpayPath(), params);
        requireSuccess(response, "聚合收银台下单");
        String qrUrl = response.get("qr_url");
        if (StrUtil.isBlank(qrUrl)) {
            log.error("扫呗聚合码未返回收银台链接：payNo={} response={}", payment.getPayNo(), response);
            throw new BizException(ErrorCode.PAY_FAILED.getCode(), "支付收银台生成失败");
        }
        return qrUrl;
    }

    @Override
    public void close(Payment payment) {
        requireConfigured();
        Map<String, String> response = post(properties.getClosePath(), baseParams(payment));
        if (!SUCCESS.equals(response.get("return_code"))) {
            throw new BizException(ErrorCode.PAY_FAILED.getCode(), message(response, "支付关单失败"));
        }
        if (!SUCCESS.equals(response.get("result_code"))) {
            // 已关闭或状态已变化时不阻断，统一以随后的 4.5 查单结果为准
            log.warn("扫呗关单未成功，转入查单确认：payNo={} response={}", payment.getPayNo(), response);
        }
    }

    @Override
    public PaymentQueryResult query(Payment payment) {
        requireConfigured();
        Map<String, String> response = post(properties.getQueryPath(), baseParams(payment));
        if (!SUCCESS.equals(response.get("return_code"))) {
            throw new BizException(ErrorCode.PAY_FAILED.getCode(), message(response, "支付查单失败"));
        }
        if (!SUCCESS.equals(response.get("result_code"))) {
            log.info("扫呗查单未命中：payNo={} response={}", payment.getPayNo(), response);
            return new PaymentQueryResult(false, "", null);
        }
        boolean paid = TRADE_SUCCESS.equalsIgnoreCase(StrUtil.nullToEmpty(response.get("trade_state")));
        return new PaymentQueryResult(paid, StrUtil.nullToEmpty(response.get("out_trade_no")),
                parsePaidAt(response.get("pay_time")));
    }

    @Override
    public boolean verifyNotify(Map<String, String> params) {
        String received = params.get("key_sign");
        if (StrUtil.isBlank(received) || StrUtil.isBlank(properties.getAccessToken())) {
            return false;
        }
        return received.equalsIgnoreCase(sign(params, properties.getAccessToken()));
    }

    /**
     * 扫呗签名：参数按名升序拼接 k=v&...，追加密钥后取 MD5 小写。
     */
    static String sign(Map<String, String> params, String accessToken) {
        String content = params.entrySet().stream()
                .filter(entry -> !"key_sign".equals(entry.getKey()))
                .filter(entry -> StrUtil.isNotBlank(entry.getValue()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        return DigestUtil.md5Hex(content + "&key=" + StrUtil.nullToEmpty(accessToken));
    }

    private Map<String, String> baseParams(Payment payment) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pay_ver", properties.getPayVer());
        params.put("pay_type", properties.getPayType());
        if (StrUtil.isNotBlank(properties.getServiceId())) {
            params.put("service_id", properties.getServiceId());
        }
        params.put("merchant_no", properties.getMerchantNo());
        params.put("terminal_id", properties.getTerminalId());
        params.put("terminal_trace", payment.getPayNo());
        // 终端交易时间取支付单创建时间：查单(4.5 方式二)/关单(4.8)必须用下单时的原值才能命中订单
        params.put("terminal_time", TERMINAL_TIME.format(terminalTime(payment)));
        return params;
    }

    private LocalDateTime terminalTime(Payment payment) {
        return payment.getCreatedAt() == null ? LocalDateTime.now() : payment.getCreatedAt();
    }

    /**
     * 订单描述：去除扫呗禁止的特殊符号（+ 空格 / ? % # & = 【），超长截断。
     */
    private String sanitizeOrderBody(Payment payment) {
        String body = "订单支付 " + payment.getPayNo();
        return body.replaceAll("[+ /?%#&=【]", "-");
    }

    private Map<String, String> post(String path, Map<String, String> params) {
        params.put("key_sign", sign(params, properties.getAccessToken()));
        String url = StrUtil.removeSuffix(properties.getApiBase(), "/") + path;
        Map<String, Object> form = new LinkedHashMap<>();
        params.forEach(form::put);
        String body;
        try {
            body = HttpRequest.post(url).form(form).timeout(TIMEOUT_MS).execute().body();
        } catch (Exception e) {
            log.error("扫呗请求异常：url={} payNo={} error={}", url, params.get("terminal_trace"), e.toString());
            throw new BizException(ErrorCode.PAY_FAILED.getCode(), "支付网关连接失败");
        }
        Map<String, String> response = parseBody(body);
        if (response.isEmpty()) {
            log.error("扫呗响应无法解析：url={} payNo={} body={}", url, params.get("terminal_trace"), body);
            throw new BizException(ErrorCode.PAY_FAILED.getCode(), "支付网关响应异常");
        }
        return response;
    }

    private void requireSuccess(Map<String, String> response, String action) {
        if (!SUCCESS.equals(response.get("return_code")) || !SUCCESS.equals(response.get("result_code"))) {
            log.warn("扫呗{}失败：response={}", action, response);
            throw new BizException(ErrorCode.PAY_FAILED.getCode(), message(response, action + "失败"));
        }
    }

    private String message(Map<String, String> response, String fallback) {
        String message = StrUtil.blankToDefault(response.get("return_msg"), response.get("result_msg"));
        return StrUtil.blankToDefault(message, fallback);
    }

    private Map<String, String> extractPayParams(Map<String, String> response) {
        Map<String, String> payParams = new LinkedHashMap<>();
        for (String key : List.of("pay_params", "pay_info")) {
            String value = response.get(key);
            if (StrUtil.isBlank(value)) {
                continue;
            }
            try {
                JSONObject object = JSONUtil.parseObj(value);
                object.forEach((name, item) -> payParams.put(name, item == null ? "" : String.valueOf(item)));
            } catch (Exception e) {
                log.warn("扫呗支付参数解析失败：key={} value={}", key, value);
            }
        }
        return payParams;
    }

    private Map<String, String> parseBody(String body) {
        Map<String, String> result = new LinkedHashMap<>();
        if (StrUtil.isBlank(body)) {
            return result;
        }
        String content = body.trim();
        if (content.startsWith("{")) {
            JSONObject object = JSONUtil.parseObj(content);
            object.forEach((key, value) -> result.put(key, value == null ? "" : String.valueOf(value)));
            return result;
        }
        for (String pair : content.split("&")) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, index), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    private LocalDateTime parsePaidAt(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        for (DateTimeFormatter formatter : List.of(TERMINAL_TIME, DATE_TIME)) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (Exception ignored) {
                // 尝试下一种格式
            }
        }
        return null;
    }

    private void requireConfigured() {
        if (StrUtil.isBlank(properties.getMerchantNo()) || StrUtil.isBlank(properties.getTerminalId())
                || StrUtil.isBlank(properties.getAccessToken())) {
            throw new BizException(ErrorCode.PAY_FAILED.getCode(), "扫呗支付参数未配置");
        }
    }
}

package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.Header;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 扫呗支付网关（普通支付 2.0，pay.lcsw.cn）：
 * 4.11 聚合码下单、4.3 公众号 JSAPI、4.5 查单、4.8 关单、4.10 回调验签。
 * 通信协议 application/json；签名 = 字典序参数 + "&access_token=终端令牌" 后 MD5 小写。
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

    /** 4.11 聚合码下单，pay_ver 202 */
    private static final String SERVICE_QRPAY = "016";
    /** 4.3 公众号下单，pay_ver 202 */
    private static final String SERVICE_JSAPI = "012";
    /** 4.5 查单，pay_ver 202 */
    private static final String SERVICE_QUERY = "020";
    /** 4.8 关单，pay_ver 201（注意与其它接口不同） */
    private static final String SERVICE_CLOSE = "041";
    private static final String VER_CURRENT = "202";
    private static final String VER_CLOSE = "201";

    private final SaobeiProperties properties;

    @Override
    public JsapiResult jsapi(Payment payment, String openId) {
        requireConfigured();
        Map<String, String> params = requestBase(VER_CURRENT, properties.getPayType(), SERVICE_JSAPI,
                payment.getPayNo(), terminalTime(payment));
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
        Map<String, String> params = requestBase(VER_CURRENT, "000", SERVICE_QRPAY,
                payment.getPayNo(), terminalTime(payment));
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
        // 4.8：terminal_trace/time 是本次关单请求自己的新流水号与实时时间；目标订单走 out_trade_no 或 pay_trace+pay_time
        Map<String, String> params = requestBase(VER_CLOSE, "000", SERVICE_CLOSE,
                newTrace(), LocalDateTime.now());
        if (StrUtil.isNotBlank(payment.getOutTradeNo())) {
            params.put("out_trade_no", payment.getOutTradeNo());
        } else {
            params.put("pay_trace", payment.getPayNo());
            params.put("pay_time", TERMINAL_TIME.format(terminalTime(payment)));
        }
        Map<String, String> response = post(properties.getClosePath(), params);
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
        // 4.5：terminal_trace/time 为本次查询请求的新流水号；查询目标 = out_trade_no 或 pay_trace+pay_time（下单时的原值）
        Map<String, String> params = requestBase(VER_CURRENT, "000", SERVICE_QUERY,
                newTrace(), LocalDateTime.now());
        if (StrUtil.isNotBlank(payment.getOutTradeNo())) {
            params.put("out_trade_no", payment.getOutTradeNo());
        } else {
            params.put("pay_trace", payment.getPayNo());
            params.put("pay_time", TERMINAL_TIME.format(terminalTime(payment)));
        }
        Map<String, String> response = post(properties.getQueryPath(), params);
        if (!SUCCESS.equals(response.get("return_code"))) {
            // 聚合码 auto_pay 下单后用户未扫码时订单尚未生成，扫呗返回"订单信息不存在"，属正常未支付状态
            String msg = StrUtil.nullToEmpty(response.get("return_msg"));
            if (msg.contains("订单信息不存在")) {
                return new PaymentQueryResult(false, "", null);
            }
            throw new BizException(ErrorCode.PAY_FAILED.getCode(), message(response, "支付查单失败"));
        }
        if (!SUCCESS.equals(response.get("result_code"))) {
            // result_code=02 失败（无需继续查询）、03 支付中：均视为未支付
            log.info("扫呗查单未命中：payNo={} msg={}", payment.getPayNo(), response.get("return_msg"));
            return new PaymentQueryResult(false, "", null);
        }
        boolean paid = TRADE_SUCCESS.equalsIgnoreCase(StrUtil.nullToEmpty(response.get("trade_state")));
        return new PaymentQueryResult(paid, StrUtil.nullToEmpty(response.get("out_trade_no")),
                parsePaidAt(StrUtil.blankToDefault(response.get("end_time"), response.get("pay_time"))));
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
     * 扫呗签名：参数按名升序拼接 k=v&...，末尾追加 &access_token=终端令牌 后取 MD5 小写。
     * 空值不参与签名（本方请求不传空值；回调报文空值由扫呗侧同规则处理）。
     */
    static String sign(Map<String, String> params, String accessToken) {
        String content = params.entrySet().stream()
                .filter(entry -> !"key_sign".equals(entry.getKey()))
                .filter(entry -> StrUtil.isNotBlank(entry.getValue()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        return DigestUtil.md5Hex(content + "&access_token=" + StrUtil.nullToEmpty(accessToken));
    }

    /**
     * 请求公共参数：pay_ver / pay_type / service_id / 商户终端号 / 本次请求流水号与时间。
     */
    private Map<String, String> requestBase(String payVer, String payType, String serviceId,
                                            String terminalTrace, LocalDateTime terminalTime) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pay_ver", payVer);
        params.put("pay_type", payType);
        params.put("service_id", serviceId);
        params.put("merchant_no", properties.getMerchantNo());
        params.put("terminal_id", properties.getTerminalId());
        params.put("terminal_trace", terminalTrace);
        params.put("terminal_time", TERMINAL_TIME.format(terminalTime));
        return params;
    }

    /**
     * 查单/关单请求流水号：每次请求新生成（6-32 位）。
     */
    private String newTrace() {
        return "Q" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 999);
    }

    /**
     * 下单时间：取支付单创建时间，保证查单方式二（pay_trace+pay_time）/关单命中原订单。
     */
    private LocalDateTime terminalTime(Payment payment) {
        return payment.getCreatedAt() == null ? LocalDateTime.now() : payment.getCreatedAt();
    }

    /**
     * 订单描述：去除扫呗禁止的特殊符号（+ 空格 / ? % # & = 【），超长截断。
     */
    private String sanitizeOrderBody(Payment payment) {
        String body = "订单支付-" + payment.getPayNo();
        return body.replaceAll("[+ /?%#&=【]", "-");
    }

    private Map<String, String> post(String path, Map<String, String> params) {
        params.put("key_sign", sign(params, properties.getAccessToken()));
        String url = StrUtil.removeSuffix(properties.getApiBase(), "/") + path;
        // 扫呗 2.0 通信协议为 application/json，表单提交返回 415
        String body;
        try {
            body = HttpRequest.post(url)
                    .header(Header.CONTENT_TYPE, "application/json")
                    .body(JSONUtil.toJsonStr(params))
                    .timeout(TIMEOUT_MS)
                    .execute()
                    .body();
        } catch (Exception e) {
            log.error("扫呗请求异常：url={} payNo={} error={}", url, params.get("pay_trace"), e.toString());
            throw new BizException(ErrorCode.PAY_FAILED.getCode(), "支付网关连接失败");
        }
        Map<String, String> response = parseBody(body);
        if (response.isEmpty()) {
            log.error("扫呗响应无法解析：url={} trace={} body={}", url, params.get("terminal_trace"), body);
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
        for (DateTimeFormatter formatter : List.of(TERMINAL_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))) {
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

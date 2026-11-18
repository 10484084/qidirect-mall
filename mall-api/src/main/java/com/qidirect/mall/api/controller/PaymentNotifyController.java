package com.qidirect.mall.api.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.infra.service.PaymentNotifyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 扫呗 4.10 支付回调入口。按扫呗约定返回 return_code=01 表示已处理，其余情况由其重试。
 */
@Slf4j
@RestController
@RequestMapping("/notify")
@RequiredArgsConstructor
public class PaymentNotifyController {

    private static final String SUCCESS = "01";
    private static final String FAILED = "02";

    private final PaymentNotifyService paymentNotifyService;

    @PostMapping("/saobei")
    public Map<String, String> saobei(HttpServletRequest request) {
        String rawBody = readBody(request);
        Map<String, String> params = resolveParams(rawBody, request);
        try {
            paymentNotifyService.handleSaobei(params, rawBody);
            return Map.of("return_code", SUCCESS, "return_msg", "OK");
        } catch (BizException e) {
            log.warn("支付回调处理失败：payNo={} msg={}", params.get("terminal_trace"), e.getMessage());
            return Map.of("return_code", FAILED, "return_msg", StrUtil.nullToEmpty(e.getMessage()));
        }
    }

    private Map<String, String> resolveParams(String rawBody, HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        if (StrUtil.isBlank(rawBody)) {
            return params;
        }
        String content = rawBody.trim();
        if (content.startsWith("{")) {
            JSONObject object = JSONUtil.parseObj(content);
            object.forEach((key, value) ->
                    params.put(key, value == null ? "" : String.valueOf(value)));
            return params;
        }
        for (String pair : content.split("&")) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, index), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }

    private String readBody(HttpServletRequest request) {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        } catch (IOException e) {
            log.warn("读取支付回调报文失败：{}", e.toString());
        }
        return body.toString();
    }
}

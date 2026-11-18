package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.infra.config.WechatProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 公众号网页授权：授权地址以 state 携带 pay_no，回跳前端 bridge 页换取 openid。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatAuthService {

    private static final int TIMEOUT_MS = 10_000;

    private final WechatProperties properties;

    public String authorizeUrl(String state) {
        requireConfigured();
        return properties.getAuthorizeUrl()
                + "?appid=" + encode(properties.getAppId())
                + "&redirect_uri=" + encode(properties.getBridgeUrl())
                + "&response_type=code&scope=snsapi_base&state=" + encode(state)
                + "#wechat_redirect";
    }

    public String exchangeOpenId(String code) {
        requireConfigured();
        if (StrUtil.isBlank(code)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "缺少微信授权 code");
        }
        String url = properties.getTokenUrl()
                + "?appid=" + encode(properties.getAppId())
                + "&secret=" + encode(properties.getSecret())
                + "&code=" + encode(code)
                + "&grant_type=authorization_code";
        String body;
        try {
            body = HttpUtil.get(url, TIMEOUT_MS);
        } catch (Exception e) {
            log.error("微信授权请求失败：error={}", e.toString());
            throw new BizException(ErrorCode.PAY_FAILED.getCode(), "微信授权失败，请重试");
        }
        JSONObject response = JSONUtil.parseObj(body);
        String openId = response.getStr("openid");
        if (StrUtil.isBlank(openId)) {
            log.warn("微信授权未返回 openid：{}", body);
            throw new BizException(ErrorCode.PAY_FAILED.getCode(), "微信授权失败，请重试");
        }
        return openId;
    }

    private void requireConfigured() {
        if (StrUtil.isBlank(properties.getAppId()) || StrUtil.isBlank(properties.getSecret())
                || StrUtil.isBlank(properties.getBridgeUrl())) {
            throw new BizException(ErrorCode.PAY_FAILED.getCode(), "微信公众号支付未配置");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(StrUtil.nullToEmpty(value), StandardCharsets.UTF_8);
    }
}

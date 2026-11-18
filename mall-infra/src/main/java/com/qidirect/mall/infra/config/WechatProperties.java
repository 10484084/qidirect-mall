package com.qidirect.mall.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信公众号网页授权配置，用于 JSAPI 支付获取 openid。
 */
@Data
@Component
@ConfigurationProperties(prefix = "payment.wechat")
public class WechatProperties {

    private String appId = "";

    private String secret = "";

    /** 授权回跳的前端 bridge 页地址，state 携带 pay_no */
    private String bridgeUrl = "";

    private String authorizeUrl = "https://open.weixin.qq.com/connect/oauth2/authorize";

    private String tokenUrl = "https://api.weixin.qq.com/sns/oauth2/access_token";
}

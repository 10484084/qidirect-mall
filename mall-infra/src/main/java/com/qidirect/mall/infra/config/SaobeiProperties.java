package com.qidirect.mall.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 扫呗支付网关配置。签名令牌仅通过环境变量注入。
 */
@Data
@Component
@ConfigurationProperties(prefix = "payment.saobei")
public class SaobeiProperties {

    /** 网关根地址，不含具体接口路径 */
    private String apiBase = "https://pay.lcsw.cn/lcsw/pay";

    private String merchantNo = "";

    private String terminalId = "";

    /** 终端令牌，参与签名 */
    private String accessToken = "";

    /** 服务商编号，直连商户可留空 */
    private String serviceId = "";

    private String notifyUrl = "";

    private String payVer = "100";

    /** 010 微信 */
    private String payType = "010";

    private String jsapiPath = "/pay/open/wx/jsapi";

    private String queryPath = "/pay/open/merchant/query";

    private String closePath = "/pay/open/merchant/cancel";
}

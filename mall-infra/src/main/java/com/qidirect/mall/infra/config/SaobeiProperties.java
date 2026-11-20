package com.qidirect.mall.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 扫呗支付网关配置（普通支付 2.0，pay.lcsw.cn）。签名令牌仅通过环境变量注入。
 */
@Data
@Component
@ConfigurationProperties(prefix = "payment.saobei")
public class SaobeiProperties {

    /** 网关根地址，不含具体接口路径（完整 URL 形如 {apiBase}/pay/open/qrpay） */
    private String apiBase = "https://pay.lcsw.cn/lcsw";

    private String merchantNo = "";

    private String terminalId = "";

    /** 终端令牌，参与签名 */
    private String accessToken = "";

    /** 接口类型编号：012 公众号下单、016 聚合码、020 查单。直连商户可留空（接口内部兜底） */
    private String serviceId = "";

    /** 4.10 异步回调地址，必须为 https 域名（扫呗拒绝 IP/http/localhost） */
    private String notifyUrl = "";

    /** 接口内业务逻辑兼容号，普通支付 2.0 为 202 */
    private String payVer = "202";

    /** 010 微信 */
    private String payType = "010";

    /** 4.3 公众号下单（暂未启用，保留） */
    private String jsapiPath = "/pay/open/jspay";

    /** 4.11 聚合码下单，返回收银台链接 qr_url */
    private String qrpayPath = "/pay/open/qrpay";

    /** 4.5 支付查询（方式二：terminal_trace + terminal_time） */
    private String queryPath = "/pay/open/query";

    /** 4.8 关闭订单 */
    private String closePath = "/pay/open/close";
}

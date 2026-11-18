package com.qidirect.mall.infra.sms;

import java.util.Map;

/**
 * 短信网关。模板与内容后续存于 sms_template 表，由业务按 code 选择模板。
 */
public interface SmsGateway {

    void send(String phone, String templateCode, Map<String, String> params);
}

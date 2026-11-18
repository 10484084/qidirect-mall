package com.qidirect.mall.infra.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 开发与联调期使用的 Mock 短信网关，仅打印日志。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "sms.provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsGateway implements SmsGateway {

    @Override
    public void send(String phone, String templateCode, Map<String, String> params) {
        log.info("[MockSms] phone={} template={} params={}", phone, templateCode, params);
    }
}

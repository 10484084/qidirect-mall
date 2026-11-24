package com.qidirect.mall.infra.identity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 未配置身份核验服务时的兜底实现：只记日志并放行，避免开发与联调环境被卡住。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "identity.provider", havingValue = "mock", matchIfMissing = true)
public class MockIdentityVerifyGateway implements IdentityVerifyGateway {

    @Override
    public boolean verify(String realName, String idCard) {
        log.info("[MockIdentity] 跳过实名核验 name={} idCard={}****", realName, idCard.substring(0, 4));
        return true;
    }
}

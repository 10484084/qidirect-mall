package com.qidirect.mall.infra.identity;

import com.aliyun.auth.credentials.Credential;
import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import com.aliyun.sdk.service.cloudauth20190307.AsyncClient;
import com.aliyun.sdk.service.cloudauth20190307.models.Id2MetaVerifyRequest;
import com.aliyun.sdk.service.cloudauth20190307.models.Id2MetaVerifyResponse;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import darabonba.core.client.ClientOverrideConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 阿里云身份二要素核验（Id2MetaVerify）：姓名 + 身份证号比对公安库，不做人脸。
 * 通过 identity.provider=aliyun 启用。
 * bizCode：1 一致，2 不一致，3 查无此人。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "identity.provider", havingValue = "aliyun")
public class AliIdentityVerifyGateway implements IdentityVerifyGateway {

    private static final String BIZ_MATCH = "1";

    private final AsyncClient client;

    public AliIdentityVerifyGateway(
            @Value("${identity.aliyun.access-key-id:}") String accessKeyId,
            @Value("${identity.aliyun.access-key-secret:}") String accessKeySecret,
            @Value("${identity.aliyun.endpoint:}") String endpoint) {
        StaticCredentialProvider provider = StaticCredentialProvider.create(
                Credential.builder().accessKeyId(accessKeyId).accessKeySecret(accessKeySecret).build());
        var builder = AsyncClient.builder()
                .region("cn-hangzhou")
                .credentialsProvider(provider);
        if (endpoint != null && !endpoint.isBlank()) {
            builder = builder.overrideConfiguration(
                    ClientOverrideConfiguration.create().setEndpointOverride(endpoint));
        }
        this.client = builder.build();
        log.info("[Identity] 身份二要素核验已启用 endpoint={}", endpoint == null || endpoint.isBlank() ? "auto" : endpoint);
    }

    @Override
    public boolean verify(String realName, String idCard) {
        Id2MetaVerifyRequest request = Id2MetaVerifyRequest.builder()
                .paramType("normal")
                .userName(realName)
                .identifyNum(idCard)
                .build();
        try {
            Id2MetaVerifyResponse response = client.id2MetaVerify(request).get(10, TimeUnit.SECONDS);
            Integer httpCode = response.getStatusCode();
            if (httpCode == null || httpCode != 200) {
                log.error("[Identity] 核验失败 httpCode={} name={}", httpCode, realName);
                throw new BizException(ErrorCode.SMS_SEND_FAILED.getCode(), "实名核验服务异常，请稍后重试");
            }
            String bizCode = response.getBody() == null || response.getBody().getResultObject() == null
                    ? null : response.getBody().getResultObject().getBizCode();
            log.info("[Identity] 核验结果 name={} bizCode={}", realName, bizCode);
            return BIZ_MATCH.equals(bizCode);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Identity] 核验调用异常 name={} err={}", realName, e.getMessage());
            throw new BizException(ErrorCode.SMS_SEND_FAILED.getCode(), "实名核验服务异常，请稍后重试");
        }
    }
}

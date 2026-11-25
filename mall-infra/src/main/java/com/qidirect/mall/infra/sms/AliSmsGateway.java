package com.qidirect.mall.infra.sms;

import cn.hutool.json.JSONUtil;
import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 阿里云短信网关。适用于验证码与通知类短信，签名与模板需先在控制台报备通过。
 * 通过 sms.provider=aliyun 启用；未配置时回退到 MockSmsGateway。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "sms.provider", havingValue = "aliyun")
public class AliSmsGateway implements SmsGateway {

    private final Client client;
    private final String signName;

    public AliSmsGateway(
            @Value("${sms.aliyun.access-key-id:}") String accessKeyId,
            @Value("${sms.aliyun.access-key-secret:}") String accessKeySecret,
            @Value("${sms.aliyun.sign-name:}") String signName,
            @Value("${sms.aliyun.endpoint:dysmsapi.aliyuncs.com}") String endpoint) throws Exception {
        Config config = new Config();
        config.accessKeyId = accessKeyId;
        config.accessKeySecret = accessKeySecret;
        config.endpoint = endpoint;
        this.client = new Client(config);
        this.signName = signName;
        log.info("[AliSms] 短信网关已启用，签名={} endpoint={}", signName, endpoint);
    }

    @Override
    public void send(String phone, String templateCode, Map<String, String> params) {
        SendSmsRequest request = new SendSmsRequest()
                .setPhoneNumbers(phone)
                .setSignName(signName)
                .setTemplateCode(templateCode)
                .setTemplateParam(JSONUtil.toJsonStr(params));
        try {
            SendSmsResponse response = client.sendSms(request);
            String code = response.getBody() == null ? null : response.getBody().getCode();
            if (!"OK".equalsIgnoreCase(code)) {
                String message = response.getBody() == null ? "无响应" : response.getBody().getMessage();
                log.error("[AliSms] 发送失败 phone={} template={} code={} msg={}", phone, templateCode, code, message);
                throw new BizException(ErrorCode.SMS_SEND_FAILED.getCode(), friendlyMessage(code));
            }
            log.info("[AliSms] 发送成功 phone={} template={} bizId={}", phone, templateCode,
                    response.getBody().getBizId());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AliSms] 调用异常 phone={} template={} err={}", phone, templateCode, e.getMessage());
            throw new BizException(ErrorCode.SMS_SEND_FAILED.getCode(), "短信发送失败，请稍后重试");
        }
    }

    /**
     * 把阿里云错误码转成使用者能看懂的提示，原始错误码只留在日志里。
     */
    private String friendlyMessage(String code) {
        if (code == null) {
            return "短信发送失败，请稍后重试";
        }
        return switch (code) {
            case "isv.AMOUNT_NOT_ENOUGH" -> "短信服务余额不足，请联系管理员充值";
            case "isv.BUSINESS_LIMIT_CONTROL" -> "该手机号发送过于频繁，请稍后再试";
            case "isv.MOBILE_NUMBER_ILLEGAL", "isv.MOBILE_COUNT_OVER_LIMIT" -> "手机号不正确，请检查后重试";
            case "isv.SMS_SIGNATURE_ILLEGAL", "isv.SIGN_NOT_QUALIFIED", "isv.SIGN_NOT_SAME" ->
                    "短信签名不可用，请联系管理员";
            case "isv.SMS_TEMPLATE_ILLEGAL", "isv.TEMPLATE_MISSING_PARAMETERS" ->
                    "短信模板不可用，请联系管理员";
            case "isv.DAY_LIMIT_CONTROL" -> "今日短信发送量已达上限，请明天再试";
            default -> "短信发送失败，请稍后重试";
        };
    }
}

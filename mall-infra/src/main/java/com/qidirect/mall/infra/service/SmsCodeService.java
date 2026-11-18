package com.qidirect.mall.infra.service;

import cn.hutool.core.util.RandomUtil;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.infra.sms.SmsGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * 短信验证码：5 分钟有效，同一手机号 60 秒限流。
 */
@Service
@RequiredArgsConstructor
public class SmsCodeService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration LIMIT_TTL = Duration.ofSeconds(60);
    private static final String CODE_KEY = "sms:code:";
    private static final String LIMIT_KEY = "sms:limit:";
    private static final String TEMPLATE_CODE = "LOGIN";

    private final StringRedisTemplate redisTemplate;
    private final SmsGateway smsGateway;

    public void send(String phone) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(LIMIT_KEY + phone, "1", LIMIT_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BizException(ErrorCode.SMS_TOO_FREQUENT);
        }
        String code = RandomUtil.randomNumbers(6);
        redisTemplate.opsForValue().set(CODE_KEY + phone, code, CODE_TTL);
        smsGateway.send(phone, TEMPLATE_CODE, Map.of("code", code));
    }

    /**
     * 校验成功后立即失效，确保验证码只能用一次。
     */
    public void verify(String phone, String code) {
        String saved = redisTemplate.opsForValue().get(CODE_KEY + phone);
        if (saved == null || !saved.equals(code)) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID);
        }
        redisTemplate.delete(CODE_KEY + phone);
    }
}

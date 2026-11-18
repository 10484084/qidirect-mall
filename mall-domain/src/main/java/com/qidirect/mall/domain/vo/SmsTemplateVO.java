package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;

/**
 * 短信模板视图。
 */
public record SmsTemplateVO(
        Long id,
        String code,
        String providerId,
        String content,
        Integer status,
        LocalDateTime createdAt) {
}

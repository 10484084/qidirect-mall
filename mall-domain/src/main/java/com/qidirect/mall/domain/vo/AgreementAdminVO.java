package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;

/**
 * 后台协议视图。
 */
public record AgreementAdminVO(
        Long id,
        String title,
        String version,
        String content,
        Integer status,
        LocalDateTime createdAt) {
}

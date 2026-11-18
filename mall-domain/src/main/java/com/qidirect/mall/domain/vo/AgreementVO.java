package com.qidirect.mall.domain.vo;

/**
 * 当前启用的协议。
 */
public record AgreementVO(
        Long id,
        String title,
        String version,
        String content) {
}

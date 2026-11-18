package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;

/**
 * 站内消息视图。
 */
public record NoticeVO(
        Long id,
        String title,
        String content,
        String bizType,
        String bizNo,
        Integer readed,
        LocalDateTime createdAt) {
}

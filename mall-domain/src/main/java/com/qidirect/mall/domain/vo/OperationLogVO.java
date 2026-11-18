package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;

/**
 * 后台操作日志视图。
 */
public record OperationLogVO(
        Long id,
        String operator,
        String module,
        String action,
        String target,
        String detail,
        String ip,
        LocalDateTime createdAt) {
}

package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台预存活动视图，带适用档位 ID 与名称。
 */
public record RechargeActivityAdminVO(
        Long id,
        String title,
        Integer limitPerStore,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer status,
        List<Long> levelIds,
        List<String> levelNames,
        LocalDateTime createdAt) {
}

package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预存活动新增/修改。
 */
public record RechargeActivitySaveRequest(
        @NotBlank(message = "活动标题不能为空")
        @Size(max = 64, message = "活动标题过长")
        String title,

        Integer limitPerStore,

        LocalDateTime startTime,

        LocalDateTime endTime,

        Integer status,

        @NotEmpty(message = "适用档位不能为空")
        List<@NotNull(message = "档位 ID 不能为空") Long> levelIds) {
}

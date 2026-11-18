package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 厂家实收前直接调整门店上级。
 */
public record AdminParentChangeRequest(
        @NotNull(message = "目标上级不能为空")
        Long newParentId,

        @Size(max = 255, message = "调整原因过长")
        String reason) {
}

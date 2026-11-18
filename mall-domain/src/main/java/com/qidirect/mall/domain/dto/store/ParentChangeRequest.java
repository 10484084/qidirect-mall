package com.qidirect.mall.domain.dto.store;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 门店提交改上级申请，由厂家审批。
 */
public record ParentChangeRequest(
        @NotNull(message = "目标上级不能为空")
        Long newParentId,

        @Size(max = 255, message = "申请原因过长")
        String reason) {
}

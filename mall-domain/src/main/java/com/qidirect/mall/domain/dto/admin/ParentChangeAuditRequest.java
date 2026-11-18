package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 改上级审批。
 */
public record ParentChangeAuditRequest(
        @NotNull(message = "审批结果不能为空") Boolean approved,
        @Size(max = 255, message = "审批意见过长") String remark) {
}

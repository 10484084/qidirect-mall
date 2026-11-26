package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 厂家审核取消申请。
 */
public record OrderCancelAuditRequest(
        @NotNull Boolean approved,
        @Size(max = 200, message = "备注不能超过 200 字")
        String remark) {
}

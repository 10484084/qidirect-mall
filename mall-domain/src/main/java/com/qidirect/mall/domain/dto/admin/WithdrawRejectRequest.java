package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提现驳回：必填原因，仅解冻不写余额流水。
 */
public record WithdrawRejectRequest(
        @NotBlank(message = "驳回原因不能为空")
        @Size(max = 255, message = "驳回原因过长")
        String reason) {
}

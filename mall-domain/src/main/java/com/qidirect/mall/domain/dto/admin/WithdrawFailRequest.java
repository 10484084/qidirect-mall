package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 打款失败：必填原因，解冻后退回可用余额。
 */
public record WithdrawFailRequest(
        @NotBlank(message = "失败原因不能为空")
        @Size(max = 255, message = "失败原因过长")
        String reason) {
}

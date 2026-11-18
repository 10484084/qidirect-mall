package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 系统配置修改。
 */
public record ConfigSaveRequest(
        @NotBlank(message = "配置值不能为空")
        @Size(max = 512, message = "配置值过长")
        String value,

        @Size(max = 128, message = "备注过长")
        String remark) {
}

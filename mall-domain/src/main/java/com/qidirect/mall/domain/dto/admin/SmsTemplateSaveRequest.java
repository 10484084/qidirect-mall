package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 短信模板新增/修改。code 全局唯一。
 */
public record SmsTemplateSaveRequest(
        @NotBlank(message = "模板编码不能为空")
        @Size(max = 32, message = "模板编码过长")
        String code,

        @Size(max = 64, message = "服务商模板 ID 过长")
        String providerId,

        @NotBlank(message = "模板内容不能为空")
        @Size(max = 512, message = "模板内容过长")
        String content,

        Integer status) {
}

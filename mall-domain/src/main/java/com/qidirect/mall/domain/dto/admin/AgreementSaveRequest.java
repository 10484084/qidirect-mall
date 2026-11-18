package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 协议新增/修改。version 全局唯一。
 */
public record AgreementSaveRequest(
        @NotBlank(message = "协议标题不能为空")
        @Size(max = 64, message = "协议标题过长")
        String title,

        @NotBlank(message = "协议版本不能为空")
        @Size(max = 16, message = "协议版本过长")
        String version,

        @NotBlank(message = "协议内容不能为空")
        String content,

        Integer status) {
}

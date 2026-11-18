package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 角色新增/修改。perms 保存为 JSON 数组字符串。
 */
public record AdminRoleSaveRequest(
        @NotBlank(message = "角色名称不能为空")
        @Size(max = 32, message = "角色名称过长")
        String name,

        @NotEmpty(message = "权限点不能为空")
        List<String> perms) {
}

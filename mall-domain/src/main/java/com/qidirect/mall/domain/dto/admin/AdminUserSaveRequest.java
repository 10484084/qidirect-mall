package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 管理员新增。account 全局唯一，密码使用 BCrypt 存储。
 */
public record AdminUserSaveRequest(
        @NotBlank(message = "账号不能为空")
        @Size(max = 32, message = "账号过长")
        String account,

        @NotBlank(message = "密码不能为空")
        @Size(max = 64, message = "密码过长")
        String password,

        @Size(max = 32, message = "姓名过长")
        String realName,

        Integer status,

        List<Long> roleIds) {
}

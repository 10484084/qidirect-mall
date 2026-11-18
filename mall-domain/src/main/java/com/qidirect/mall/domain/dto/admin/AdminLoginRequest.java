package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotBlank;

/**
 * 后台管理员登录。
 */
public record AdminLoginRequest(
        @NotBlank(message = "账号不能为空") String account,
        @NotBlank(message = "密码不能为空") String password) {
}

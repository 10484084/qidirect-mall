package com.qidirect.mall.domain.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户名 + 密码登录。
 */
public record PwdLoginRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 32, message = "用户名过长")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(max = 64, message = "密码过长")
        String password) {
}

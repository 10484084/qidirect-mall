package com.qidirect.mall.domain.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 忘记密码：手机号 + 短信验证码重置登录密码。
 */
public record ResetPasswordRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
        String code,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度需为 6-32 位")
        String newPassword) {
}

package com.qidirect.mall.domain.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 扫码注册。inviteCode 为空表示无上级。
 */
public record RegisterRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
        String code,

        @Size(max = 16, message = "邀请码长度不能超过 16 位")
        String inviteCode,

        @Size(max = 64, message = "昵称长度不能超过 64 位")
        String nickname) {
}

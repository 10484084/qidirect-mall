package com.qidirect.mall.domain.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 门店端修改登录密码（首次使用初始密码登录后强制调用）。
 */
public record ChangePasswordRequest(
        @NotBlank(message = "请输入原密码")
        String oldPassword,
        @NotBlank(message = "请输入新密码")
        @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
        String newPassword) {
}

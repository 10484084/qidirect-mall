package com.qidirect.mall.domain.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 自助修改手机号：必须先用原绑定手机号接收验证码。
 */
public record ChangePhoneRequest(
        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
        String code,

        @NotBlank(message = "新手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String newPhone) {
}

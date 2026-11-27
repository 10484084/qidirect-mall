package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 管理员修改。password 为空表示不修改密码，roleIds 为 null 表示不修改角色。
 */
public record AdminUserUpdateRequest(
        @Size(max = 64, message = "密码过长")
        String password,

        @Size(max = 32, message = "姓名过长")
        String realName,

        /** 绑定手机号，空串表示清除 */
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        Integer status,

        List<Long> roleIds) {
}

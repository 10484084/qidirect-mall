package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 后台新增门店：手机号唯一，可选上级。
 */
public record AdminStoreCreateRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,
        @Size(max = 32, message = "姓名过长")
        String realName,
        @Size(max = 64, message = "昵称过长")
        String nickname,
        @Size(max = 64, message = "门店名称过长")
        String name,
        Long parentId,
        @Size(max = 255, message = "备注过长")
        String remark) {
}

package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 后台特批代改收款卡，持卡人可与实名不一致，须留痕。
 */
public record BankOverrideRequest(
        @NotBlank(message = "持卡人姓名不能为空")
        @Size(max = 32, message = "持卡人姓名过长")
        String bankHolder,

        @NotBlank(message = "开户行不能为空")
        @Size(max = 64, message = "开户行名称过长")
        String bankName,

        @NotBlank(message = "银行卡号不能为空")
        @Pattern(regexp = "^\\d{8,32}$", message = "银行卡号格式不正确")
        String bankAccount,

        List<String> proofImages,

        @NotBlank(message = "特批原因不能为空")
        @Size(max = 255, message = "特批原因过长")
        String reason) {
}

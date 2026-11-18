package com.qidirect.mall.domain.dto.store;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 收款卡绑定。持卡人须与实名一致，否则只能由后台特批代改。
 */
public record StoreBankRequest(
        @NotBlank(message = "持卡人姓名不能为空")
        @Size(max = 32, message = "持卡人姓名过长")
        String bankHolder,

        @NotBlank(message = "开户行不能为空")
        @Size(max = 64, message = "开户行名称过长")
        String bankName,

        @NotBlank(message = "银行卡号不能为空")
        @Pattern(regexp = "^\\d{8,32}$", message = "银行卡号格式不正确")
        String bankAccount) {
}

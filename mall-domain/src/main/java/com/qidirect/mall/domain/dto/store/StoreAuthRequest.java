package com.qidirect.mall.domain.dto.store;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 实名信息提交。一期只采集留档，身份证与银行卡号加密存储。
 */
public record StoreAuthRequest(
        @NotBlank(message = "姓名不能为空")
        @Size(max = 32, message = "姓名过长")
        String realName,

        @NotBlank(message = "身份证号不能为空")
        @Size(max = 18, message = "身份证号格式不正确")
        String idCard,

        @Size(max = 64, message = "门店名称过长")
        String storeName,

        @Size(max = 64, message = "营业执照号过长")
        String licenseNo,

        List<String> bizImages) {
}

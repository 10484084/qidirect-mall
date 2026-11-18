package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 标记打款成功：必须上传打款凭证。
 */
public record WithdrawPaidRequest(
        @NotEmpty(message = "请上传打款凭证")
        List<String> proofImages) {
}

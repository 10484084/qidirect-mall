package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 余额调整：delta 有符号，正数入账负数出账；不计业绩与提成，必填原因。
 */
public record BalanceAdjustRequest(
        @NotNull Long storeId,
        @NotNull Integer account,
        @NotNull Long delta,
        List<String> proofImages,
        @NotBlank String remark) {
}

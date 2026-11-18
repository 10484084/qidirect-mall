package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 后台发货：1 物流需公司与单号，2 其他需上传凭证。
 */
public record OrderShipRequest(
        @NotNull @Min(1) @Max(2) Integer shipType,
        @Size(max = 64) String shipCompany,
        @Size(max = 64) String shipNo,
        List<String> shipImages,
        @Size(max = 255) String remark) {
}

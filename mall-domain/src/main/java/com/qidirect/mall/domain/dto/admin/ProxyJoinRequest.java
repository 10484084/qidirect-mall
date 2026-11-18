package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 代客加盟：仅未加盟门店可用，线下收款并强制上传凭证、证件照与纸质协议扫描件。
 */
public record ProxyJoinRequest(
        @NotNull Long storeId,
        @NotNull Long levelId,
        @NotNull Integer offlineMethod,
        @NotEmpty List<String> proofImages,
        @NotEmpty List<String> idImages,
        @NotEmpty List<String> agreementImages,
        java.time.LocalDateTime receivedAt,
        String remark) {
}

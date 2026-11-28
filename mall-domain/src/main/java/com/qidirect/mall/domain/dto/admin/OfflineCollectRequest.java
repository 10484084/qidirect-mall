package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 已开通门店线下收款：金额由系统按判定表计算，后台只读。
 */
public record OfflineCollectRequest(
        @NotNull Long storeId,
        @NotNull Long levelId,
        @NotNull Integer offlineMethod,
        java.time.LocalDateTime receivedAt,
        List<String> proofImages,
        String remark) {
}

package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;

/**
 * 后台财务：余额流水行（含门店名称便于检索）。
 */
public record AdminWalletLogVO(
        Long id,
        Long storeId,
        String storeName,
        Integer account,
        String bizType,
        Integer direction,
        Long amount,
        Long balanceAfter,
        String bizNo,
        Integer source,
        String proofImages,
        Long operatorId,
        String remark,
        LocalDateTime createdAt) {
}

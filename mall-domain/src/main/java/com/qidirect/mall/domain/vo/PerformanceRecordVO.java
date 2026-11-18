package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;

/**
 * 业绩流水明细。
 */
public record PerformanceRecordVO(
        Long id,
        Long storeId,
        String storeName,
        String month,
        Long amount,
        String bizType,
        String bizNo,
        Integer source,
        Long operatorId,
        String remark,
        LocalDateTime createdAt) {
}

package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 打款批次视图。
 */
public record WithdrawBatchVO(
        Long id,
        String batchNo,
        Long totalAmount,
        Integer count,
        Integer failCount,
        Long failAmount,
        Integer status,
        LocalDateTime exportedAt,
        LocalDateTime paidAt,
        List<String> proofImages,
        LocalDateTime createdAt) {
}

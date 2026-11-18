package com.qidirect.mall.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提成流水明细。
 */
public record CommissionRecordVO(
        Long id,
        String bizType,
        String bizNo,
        Long fromStoreId,
        String fromStoreName,
        Long toStoreId,
        String toStoreName,
        Integer level,
        BigDecimal rate,
        Long baseAmount,
        Long amount,
        Integer status,
        LocalDateTime createdAt) {
}

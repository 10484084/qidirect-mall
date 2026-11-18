package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提现记录视图。卡号始终脱敏，明文仅用于后台批次导出。
 */
public record WithdrawVO(
        Long id,
        Long storeId,
        String storeName,
        String storePhone,
        Long amount,
        String bankHolder,
        String bankName,
        String bankAccountMasked,
        Integer status,
        String rejectReason,
        String failReason,
        LocalDateTime failAt,
        Long batchId,
        String batchNo,
        List<String> proofImages,
        LocalDateTime applyTime,
        LocalDateTime auditTime,
        LocalDateTime paidTime) {
}

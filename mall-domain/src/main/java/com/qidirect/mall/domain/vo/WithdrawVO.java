package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提现记录视图。bankAccount 为完整卡号（后台审核打款需直接复制），
 * bankAccountMasked 供门店端等需要脱敏展示的场景使用。
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
        String bankAccount,
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

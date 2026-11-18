package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对账结果：预存余额、佣金余额、冻结金额三条断言，差额为 0 即平衡。
 */
public record ReconcileResultVO(
        boolean balanced,
        long depositDiff,
        long commissionDiff,
        long frozenDiff,
        List<ReconcileMismatchVO> depositMismatches,
        List<ReconcileMismatchVO> commissionMismatches,
        List<ReconcileMismatchVO> frozenMismatches,
        LocalDateTime checkedAt) {
}

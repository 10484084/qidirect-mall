package com.qidirect.mall.domain.vo;

import java.util.List;

/**
 * 月结结果：逐店幂等，失败门店单独返回便于告警与重跑。
 */
public record PerformanceSettleResultVO(
        String month,
        int candidateCount,
        int settledCount,
        int skippedCount,
        int failedCount,
        List<String> failures) {
}

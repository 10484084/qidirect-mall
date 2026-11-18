package com.qidirect.mall.job;

import com.qidirect.mall.domain.vo.PerformanceSettleResultVO;
import com.qidirect.mall.infra.service.PerformanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

/**
 * 业绩月结：每月 1 日 01:30 结算上月，逐店幂等，失败清单写入日志告警。
 */
@Component
public class PerformanceSettleJob {

    private static final Logger log = LoggerFactory.getLogger(PerformanceSettleJob.class);

    private final PerformanceService performanceService;

    public PerformanceSettleJob(PerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    @Scheduled(cron = "${performance.settle-cron:0 30 1 1 * *}")
    public void settleLastMonth() {
        String month = YearMonth.now().minusMonths(1).toString();
        PerformanceSettleResultVO result = performanceService.settle(month);
        if (result.failedCount() > 0) {
            log.error("业绩月结存在失败门店：month={} failures={}", month, result.failures());
        } else {
            log.info("业绩月结完成：month={} candidates={} settled={} skipped={}",
                    month, result.candidateCount(), result.settledCount(), result.skippedCount());
        }
    }
}

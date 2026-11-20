package com.qidirect.mall.job;

import com.qidirect.mall.common.constant.SysConfigKeys;
import com.qidirect.mall.domain.vo.PerformanceSettleResultVO;
import com.qidirect.mall.infra.service.PerformanceService;
import com.qidirect.mall.infra.service.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 业绩月结：按配置 month_settle_day（默认 1 日）在 01:30 结算上月，逐店幂等，失败清单写入日志告警。
 */
@Component
public class PerformanceSettleJob {

    private static final Logger log = LoggerFactory.getLogger(PerformanceSettleJob.class);

    private final PerformanceService performanceService;
    private final SysConfigService sysConfigService;

    public PerformanceSettleJob(PerformanceService performanceService, SysConfigService sysConfigService) {
        this.performanceService = performanceService;
        this.sysConfigService = sysConfigService;
    }

    @Scheduled(cron = "${performance.settle-cron:0 30 1 * * *}")
    public void settleLastMonth() {
        int settleDay = sysConfigService.getInt(SysConfigKeys.MONTH_SETTLE_DAY, 1);
        if (LocalDate.now().getDayOfMonth() != settleDay) {
            return;
        }
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

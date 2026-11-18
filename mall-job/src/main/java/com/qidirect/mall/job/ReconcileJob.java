package com.qidirect.mall.job;

import com.qidirect.mall.domain.vo.ReconcileResultVO;
import com.qidirect.mall.infra.service.AdminReconcileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日对账：预存余额、佣金余额、冻结金额三条断言，差额不为 0 时输出告警日志。
 */
@Component
public class ReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(ReconcileJob.class);

    private final AdminReconcileService adminReconcileService;

    public ReconcileJob(AdminReconcileService adminReconcileService) {
        this.adminReconcileService = adminReconcileService;
    }

    @Scheduled(cron = "${reconcile.cron:0 30 2 * * *}")
    public void reconcileDaily() {
        ReconcileResultVO result = adminReconcileService.check();
        if (!result.balanced()) {
            log.error("每日对账不平：预存差额={} 佣金差额={} 冻结差额={} 预存异常门店={} 佣金异常门店={} 冻结异常门店={}",
                    result.depositDiff(), result.commissionDiff(), result.frozenDiff(),
                    result.depositMismatches().size(), result.commissionMismatches().size(),
                    result.frozenMismatches().size());
            return;
        }
        log.info("每日对账平衡：checkedAt={}", result.checkedAt());
    }
}

package com.qidirect.mall.job;

import com.qidirect.mall.domain.vo.WithdrawBatchVO;
import com.qidirect.mall.infra.service.AdminWithdrawService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 提现批次超 7 天未打款告警：每天 09:00 扫描待打款批次并输出告警日志。
 */
@Component
public class WithdrawBatchAlertJob {

    private static final Logger log = LoggerFactory.getLogger(WithdrawBatchAlertJob.class);
    private static final int OVERDUE_DAYS = 7;

    private final AdminWithdrawService adminWithdrawService;

    public WithdrawBatchAlertJob(AdminWithdrawService adminWithdrawService) {
        this.adminWithdrawService = adminWithdrawService;
    }

    @Scheduled(cron = "${withdraw.batch-alert-cron:0 0 9 * * *}")
    public void alertOverdueBatches() {
        List<WithdrawBatchVO> batches = adminWithdrawService.overdueBatches(OVERDUE_DAYS);
        if (batches.isEmpty()) {
            log.info("提现批次超期检查完成：无超过 {} 天未打款批次", OVERDUE_DAYS);
            return;
        }
        log.error("提现批次超过 {} 天未打款，共 {} 个批次：{}", OVERDUE_DAYS, batches.size(),
                batches.stream().map(WithdrawBatchVO::batchNo).toList());
    }
}

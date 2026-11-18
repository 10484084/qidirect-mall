package com.qidirect.mall.job;

import com.qidirect.mall.common.constant.SysConfigKeys;
import com.qidirect.mall.infra.service.SysConfigService;
import com.qidirect.mall.infra.service.WithdrawService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 提现窗口计算：每日 00:05 按 withdraw_days 计算今日是否开放与下次开放日并写回 sys_config，
 * 开放日输出提醒日志，供运维与后台核对窗口配置是否生效。
 */
@Component
public class WithdrawWindowJob {

    private static final Logger log = LoggerFactory.getLogger(WithdrawWindowJob.class);

    private final WithdrawService withdrawService;
    private final SysConfigService sysConfigService;

    public WithdrawWindowJob(WithdrawService withdrawService, SysConfigService sysConfigService) {
        this.withdrawService = withdrawService;
        this.sysConfigService = sysConfigService;
    }

    @Scheduled(cron = "${withdraw.window-cron:0 5 0 * * *}")
    public void refreshWindow() {
        boolean open = withdrawService.refreshWindow();
        String nextOpenDate = sysConfigService.get(SysConfigKeys.WITHDRAW_NEXT_OPEN_DATE);
        if (open) {
            log.info("今日为提现开放日，下次开放日 {}", nextOpenDate);
            return;
        }
        log.info("今日非提现开放日，下次开放日 {}", nextOpenDate);
    }
}

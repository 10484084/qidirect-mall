package com.qidirect.mall.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务进程，无 Web 端口。任务清单：支付补偿与采购单超时关单、订货单超时关单、
 * 业绩月结、提现窗口计算、提现批次超期告警、每日对账。
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.qidirect.mall")
public class JobApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobApplication.class, args);
    }
}

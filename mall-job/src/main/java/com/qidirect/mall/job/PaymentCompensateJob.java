package com.qidirect.mall.job;

import com.qidirect.mall.infra.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付补偿与加盟/补差/预存单超时关单。订货单超时由 OrderTimeoutJob 处理。
 */
@Component
public class PaymentCompensateJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentCompensateJob.class);

    private final PaymentService paymentService;

    public PaymentCompensateJob(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(initialDelayString = "${payment.compensate-initial-delay-ms:60000}",
            fixedDelayString = "${payment.compensate-ms:60000}")
    public void run() {
        int completed = paymentService.compensate();
        int closed = paymentService.closeExpiredPurchases();
        if (completed > 0 || closed > 0) {
            log.info("支付补偿完成：补记 {} 笔，超时关单 {} 笔", completed, closed);
        }
    }
}

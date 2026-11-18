package com.qidirect.mall.job;

import com.qidirect.mall.infra.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时未支付订货单扫描，实际取消逻辑与门店主动取消完全复用。
 */
@Component
public class OrderTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutJob.class);

    private final OrderService orderService;

    public OrderTimeoutJob(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(initialDelayString = "${order.timeout-scan-initial-delay-ms:30000}",
            fixedDelayString = "${order.timeout-scan-ms:60000}")
    public void cancelTimeoutOrders() {
        int handled = orderService.cancelTimeoutOrders();
        if (handled > 0) {
            log.info("超时订单扫描完成，本次处理 {} 笔", handled);
        }
    }
}

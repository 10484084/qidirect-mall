package com.qidirect.mall.api.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.infra.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发联调用的支付 Mock：仅 payment.provider=mock 时生效，直接把支付单置成功并分发业务。
 * 任务 8 接入扫呗后由真实回调替代。
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.provider", havingValue = "mock", matchIfMissing = true)
@RequestMapping("/notify/mock")
public class MockPayController {

    private final PurchaseService purchaseService;

    @RequestMapping(value = "/pay/{payNo}", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<Void> pay(@PathVariable String payNo) {
        purchaseService.mockPaid(payNo);
        return ApiResponse.ok();
    }
}

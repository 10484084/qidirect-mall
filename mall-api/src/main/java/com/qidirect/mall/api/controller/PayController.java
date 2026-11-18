package com.qidirect.mall.api.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.domain.dto.store.PayJsapiRequest;
import com.qidirect.mall.domain.vo.PayPrepayVO;
import com.qidirect.mall.domain.vo.PaymentStatusVO;
import com.qidirect.mall.infra.security.SecurityUtils;
import com.qidirect.mall.infra.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门店端支付：获取公众号授权地址、用授权 code 调起 JSAPI、轮询支付结果。
 */
@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
public class PayController {

    private final PaymentService paymentService;

    @GetMapping("/{payNo}/authorize")
    public ApiResponse<PayPrepayVO> authorize(@PathVariable String payNo) {
        return ApiResponse.ok(paymentService.prepayInfo(SecurityUtils.currentStoreId(), payNo));
    }

    @PostMapping("/{payNo}/jsapi")
    public ApiResponse<PayPrepayVO> jsapi(@PathVariable String payNo,
                                          @RequestBody(required = false) PayJsapiRequest request) {
        String code = request == null ? null : request.code();
        return ApiResponse.ok(paymentService.jsapi(SecurityUtils.currentStoreId(), payNo, code));
    }

    @GetMapping("/{payNo}/status")
    public ApiResponse<PaymentStatusVO> status(@PathVariable String payNo) {
        return ApiResponse.ok(paymentService.status(SecurityUtils.currentStoreId(), payNo));
    }
}

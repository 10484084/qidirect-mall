package com.qidirect.mall.api.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.domain.dto.store.PurchaseRequest;
import com.qidirect.mall.domain.vo.AgreementVO;
import com.qidirect.mall.domain.vo.PurchaseOptionVO;
import com.qidirect.mall.domain.vo.PurchaseOrderVO;
import com.qidirect.mall.infra.security.SecurityUtils;
import com.qidirect.mall.infra.service.PurchaseService;
import com.qidirect.mall.infra.util.ClientIpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 门店端统一购买：档位选项、协议正文、创建开通/补差/预存单。
 */
@RestController
@RequestMapping("/purchase")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    @GetMapping("/options")
    public ApiResponse<List<PurchaseOptionVO>> options() {
        return ApiResponse.ok(purchaseService.options(SecurityUtils.currentStoreId()));
    }

    @GetMapping("/agreement")
    public ApiResponse<AgreementVO> agreement() {
        return ApiResponse.ok(purchaseService.agreement());
    }

    @PostMapping("/orders")
    public ApiResponse<PurchaseOrderVO> create(@Valid @RequestBody PurchaseRequest request,
                                               HttpServletRequest servletRequest) {
        return ApiResponse.ok(purchaseService.createOnlineOrder(
                SecurityUtils.currentStoreId(), request, ClientIpUtils.resolve(servletRequest)));
    }
}

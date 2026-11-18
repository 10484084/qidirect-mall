package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.domain.dto.admin.OfflineCollectRequest;
import com.qidirect.mall.domain.dto.admin.ProxyJoinRequest;
import com.qidirect.mall.domain.vo.PurchaseOrderVO;
import com.qidirect.mall.infra.security.SecurityUtils;
import com.qidirect.mall.infra.service.PurchaseService;
import com.qidirect.mall.infra.util.ClientIpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台代客加盟与已加盟门店线下收款。
 */
@RestController
@RequestMapping("/purchase")
@RequiredArgsConstructor
public class AdminPurchaseController {

    private final PurchaseService purchaseService;

    @PostMapping("/proxy-join")
    public ApiResponse<PurchaseOrderVO> proxyJoin(@Valid @RequestBody ProxyJoinRequest request,
                                                  HttpServletRequest servletRequest) {
        return ApiResponse.ok(purchaseService.proxyJoin(request, SecurityUtils.currentUserId(),
                ClientIpUtils.resolve(servletRequest)));
    }

    @PostMapping("/offline-collect")
    public ApiResponse<PurchaseOrderVO> offlineCollect(@Valid @RequestBody OfflineCollectRequest request,
                                                       HttpServletRequest servletRequest) {
        return ApiResponse.ok(purchaseService.offlineCollect(request, SecurityUtils.currentUserId(),
                ClientIpUtils.resolve(servletRequest)));
    }
}

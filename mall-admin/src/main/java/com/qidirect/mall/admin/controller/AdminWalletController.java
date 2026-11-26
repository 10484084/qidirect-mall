package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.domain.dto.admin.BalanceAdjustRequest;
import com.qidirect.mall.infra.service.AdminWalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台钱包余额调整（不计业绩与提成）。
 */
@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class AdminWalletController {

    private final AdminWalletService adminWalletService;

    @PostMapping("/adjust")
    @PreAuthorize("@perm.has('wallet:adjust')")
    public ApiResponse<Long> adjust(@Valid @RequestBody BalanceAdjustRequest request) {
        return ApiResponse.ok(adminWalletService.adjust(request));
    }
}

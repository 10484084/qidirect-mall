package com.qidirect.mall.api.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.domain.vo.WalletLogVO;
import com.qidirect.mall.domain.vo.WalletVO;
import com.qidirect.mall.infra.security.SecurityUtils;
import com.qidirect.mall.infra.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门店端：钱包概览与余额流水分页。
 */
@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public ApiResponse<WalletVO> wallet() {
        return ApiResponse.ok(walletService.getWallet(SecurityUtils.currentStoreId()));
    }

    @GetMapping("/logs")
    public ApiResponse<PageResult<WalletLogVO>> logs(
            @RequestParam(required = false) Integer account,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(walletService.logs(SecurityUtils.currentStoreId(), account, page, size));
    }
}

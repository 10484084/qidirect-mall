package com.qidirect.mall.api.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.domain.dto.store.WithdrawApplyRequest;
import com.qidirect.mall.domain.vo.WithdrawConfigVO;
import com.qidirect.mall.domain.vo.WithdrawVO;
import com.qidirect.mall.infra.security.SecurityUtils;
import com.qidirect.mall.infra.service.WithdrawService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门店端提现：申请页配置、提交申请与提现记录。
 */
@RestController
@RequestMapping("/withdraws")
@RequiredArgsConstructor
public class WithdrawController {

    private final WithdrawService withdrawService;

    @GetMapping("/config")
    public ApiResponse<WithdrawConfigVO> config() {
        return ApiResponse.ok(withdrawService.config(SecurityUtils.currentStoreId()));
    }

    @PostMapping
    public ApiResponse<WithdrawVO> apply(@Valid @RequestBody WithdrawApplyRequest request) {
        return ApiResponse.ok(withdrawService.apply(SecurityUtils.currentStoreId(), request));
    }

    @GetMapping
    public ApiResponse<PageResult<WithdrawVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(withdrawService.page(SecurityUtils.currentStoreId(), page, size));
    }
}

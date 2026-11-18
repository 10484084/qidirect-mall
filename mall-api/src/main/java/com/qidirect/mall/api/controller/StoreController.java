package com.qidirect.mall.api.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.domain.dto.store.ParentChangeRequest;
import com.qidirect.mall.domain.dto.store.StoreAuthRequest;
import com.qidirect.mall.domain.dto.store.StoreBankRequest;
import com.qidirect.mall.domain.dto.store.StoreProfileUpdateRequest;
import com.qidirect.mall.domain.vo.InviteQrVO;
import com.qidirect.mall.domain.vo.OrderEligibilityVO;
import com.qidirect.mall.domain.vo.StoreProfileVO;
import com.qidirect.mall.infra.security.SecurityUtils;
import com.qidirect.mall.infra.service.StoreProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门店端：资料、实名、收款卡、邀请二维码、改上级申请与下单资格。
 */
@RestController
@RequestMapping("/store")
@RequiredArgsConstructor
public class StoreController {

    private final StoreProfileService storeProfileService;

    @GetMapping("/profile")
    public ApiResponse<StoreProfileVO> profile() {
        return ApiResponse.ok(storeProfileService.profile(SecurityUtils.currentStoreId()));
    }

    @PutMapping("/profile")
    public ApiResponse<StoreProfileVO> updateProfile(
            @Valid @RequestBody StoreProfileUpdateRequest request) {
        return ApiResponse.ok(storeProfileService.updateProfile(SecurityUtils.currentStoreId(), request));
    }

    @PostMapping("/auth")
    public ApiResponse<StoreProfileVO> submitAuth(@Valid @RequestBody StoreAuthRequest request) {
        return ApiResponse.ok(storeProfileService.submitAuth(SecurityUtils.currentStoreId(), request));
    }

    @PutMapping("/bank")
    public ApiResponse<StoreProfileVO> bindBank(@Valid @RequestBody StoreBankRequest request) {
        return ApiResponse.ok(storeProfileService.bindBank(SecurityUtils.currentStoreId(), request));
    }

    @GetMapping("/invite-qr")
    public ApiResponse<InviteQrVO> inviteQr() {
        return ApiResponse.ok(storeProfileService.inviteQr(SecurityUtils.currentStoreId()));
    }

    @PostMapping("/parent-change")
    public ApiResponse<Void> submitParentChange(@Valid @RequestBody ParentChangeRequest request) {
        storeProfileService.submitParentChange(SecurityUtils.currentStoreId(), request);
        return ApiResponse.ok();
    }

    @GetMapping("/order-eligibility")
    public ApiResponse<OrderEligibilityVO> orderEligibility() {
        return ApiResponse.ok(storeProfileService.orderEligibility(SecurityUtils.currentStoreId()));
    }
}

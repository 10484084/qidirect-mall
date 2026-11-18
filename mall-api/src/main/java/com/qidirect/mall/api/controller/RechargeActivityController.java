package com.qidirect.mall.api.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.domain.vo.RechargeActivityVO;
import com.qidirect.mall.infra.security.SecurityUtils;
import com.qidirect.mall.infra.service.RechargeActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 门店端：上架中的预存活动。
 */
@RestController
@RequestMapping("/recharge-activities")
@RequiredArgsConstructor
public class RechargeActivityController {

    private final RechargeActivityService rechargeActivityService;

    @GetMapping
    public ApiResponse<List<RechargeActivityVO>> list() {
        return ApiResponse.ok(rechargeActivityService.list(SecurityUtils.currentStoreId()));
    }
}

package com.qidirect.mall.api.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.domain.vo.StorePerformanceVO;
import com.qidirect.mall.infra.security.SecurityUtils;
import com.qidirect.mall.infra.service.PerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门店端：月度团队业绩、阶梯进度、调整明细与奖励记录。
 */
@RestController
@RequestMapping("/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;

    @GetMapping
    public ApiResponse<StorePerformanceVO> performance(
            @RequestParam(required = false) String month) {
        return ApiResponse.ok(performanceService.storePerformance(SecurityUtils.currentStoreId(), month));
    }
}

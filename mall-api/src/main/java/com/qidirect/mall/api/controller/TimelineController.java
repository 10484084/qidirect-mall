package com.qidirect.mall.api.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.domain.vo.StoreEventVO;
import com.qidirect.mall.infra.security.SecurityUtils;
import com.qidirect.mall.infra.service.StoreTimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门店端统一时间线：加盟 / 预存 / 订货 / 提成 / 业绩 / 提现。
 */
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class TimelineController {

    private final StoreTimelineService storeTimelineService;

    @GetMapping
    public ApiResponse<PageResult<StoreEventVO>> page(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(storeTimelineService.storePage(
                SecurityUtils.currentStoreId(), type, page, size));
    }
}

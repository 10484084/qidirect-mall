package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.domain.dto.admin.CommissionRateRequest;
import com.qidirect.mall.domain.vo.CommissionConfigVO;
import com.qidirect.mall.domain.vo.CommissionRecordVO;
import com.qidirect.mall.domain.vo.CommissionStoreDetailVO;
import com.qidirect.mall.infra.service.CommissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 后台提成：比例配置、提成流水与门店提成明细。
 */
@RestController
@RequestMapping("/commission")
@RequiredArgsConstructor
public class AdminCommissionController {

    private final CommissionService commissionService;

    @GetMapping("/config")
    public ApiResponse<CommissionConfigVO> config() {
        return ApiResponse.ok(commissionService.config());
    }

    @PutMapping("/config")
    public ApiResponse<CommissionConfigVO> updateConfig(@Valid @RequestBody CommissionRateRequest request) {
        return ApiResponse.ok(commissionService.updateConfig(request));
    }

    @GetMapping("/records")
    public ApiResponse<PageResult<CommissionRecordVO>> records(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long fromStoreId,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(commissionService.page(storeId, fromStoreId, bizType,
                startTime, endTime, page, size));
    }

    @GetMapping("/stores/{storeId}")
    public ApiResponse<CommissionStoreDetailVO> storeDetail(@PathVariable Long storeId) {
        return ApiResponse.ok(commissionService.storeDetail(storeId));
    }

    @GetMapping("/stores/{storeId}/records")
    public ApiResponse<PageResult<CommissionRecordVO>> storeRecords(
            @PathVariable Long storeId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(commissionService.storeRecords(storeId, page, size));
    }
}

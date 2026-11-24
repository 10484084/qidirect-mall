package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.domain.dto.admin.RechargeActivitySaveRequest;
import com.qidirect.mall.domain.vo.RechargeActivityAdminVO;
import com.qidirect.mall.infra.service.AdminRechargeActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台预存活动管理。
 */
@RestController
@RequestMapping("/recharge-activities")
@RequiredArgsConstructor
public class AdminRechargeActivityController {

    private final AdminRechargeActivityService adminRechargeActivityService;

    @GetMapping
    public ApiResponse<PageResult<RechargeActivityAdminVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(adminRechargeActivityService.page(keyword, status, page, size));
    }

    @PostMapping
    public ApiResponse<Void> create(@Valid @RequestBody RechargeActivitySaveRequest request) {
        adminRechargeActivityService.create(request);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @Valid @RequestBody RechargeActivitySaveRequest request) {
        adminRechargeActivityService.update(id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/status")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminRechargeActivityService.changeStatus(id, status);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminRechargeActivityService.delete(id);
        return ApiResponse.ok();
    }
}

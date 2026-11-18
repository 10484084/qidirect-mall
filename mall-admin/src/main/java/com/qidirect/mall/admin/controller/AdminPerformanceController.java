package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.domain.dto.admin.PerformanceAdjustRequest;
import com.qidirect.mall.domain.dto.admin.PerformanceReversalRequest;
import com.qidirect.mall.domain.dto.admin.PerformanceRuleSaveRequest;
import com.qidirect.mall.domain.vo.PerformanceBonusVO;
import com.qidirect.mall.domain.vo.PerformanceRecordVO;
import com.qidirect.mall.domain.vo.PerformanceRuleVO;
import com.qidirect.mall.domain.vo.PerformanceSettleResultVO;
import com.qidirect.mall.infra.security.SecurityUtils;
import com.qidirect.mall.infra.service.PerformanceService;
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

import java.util.List;

/**
 * 后台业绩奖：规则 CRUD、业绩流水、月结、结算前修正与结算后冲正。
 */
@RestController
@RequestMapping("/performance")
@RequiredArgsConstructor
public class AdminPerformanceController {

    private final PerformanceService performanceService;

    @GetMapping("/rules")
    public ApiResponse<List<PerformanceRuleVO>> rules() {
        return ApiResponse.ok(performanceService.rules());
    }

    @PostMapping("/rules")
    public ApiResponse<PerformanceRuleVO> createRule(@Valid @RequestBody PerformanceRuleSaveRequest request) {
        return ApiResponse.ok(performanceService.createRule(request));
    }

    @PutMapping("/rules/{id}")
    public ApiResponse<PerformanceRuleVO> updateRule(@PathVariable Long id,
                                                     @Valid @RequestBody PerformanceRuleSaveRequest request) {
        return ApiResponse.ok(performanceService.updateRule(id, request));
    }

    @DeleteMapping("/rules/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        performanceService.deleteRule(id);
        return ApiResponse.ok();
    }

    @GetMapping("/records")
    public ApiResponse<PageResult<PerformanceRecordVO>> records(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String bizType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(performanceService.recordPage(storeId, month, bizType, page, size));
    }

    @GetMapping("/bonuses")
    public ApiResponse<PageResult<PerformanceBonusVO>> bonuses(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(performanceService.bonusPage(storeId, month, page, size));
    }

    /**
     * 结算前修正：只新增 ADJUST 业绩流水。
     */
    @PostMapping("/adjust")
    public ApiResponse<PerformanceRecordVO> adjust(@Valid @RequestBody PerformanceAdjustRequest request) {
        return ApiResponse.ok(performanceService.adjust(request, SecurityUtils.currentUserId()));
    }

    /**
     * 结算后冲正：原记录置 2，新增 revision_of 记录，钱包只记差额。
     */
    @PostMapping("/bonuses/{id}/reverse")
    public ApiResponse<PerformanceBonusVO> reverse(@PathVariable Long id,
                                                   @Valid @RequestBody PerformanceReversalRequest request) {
        return ApiResponse.ok(performanceService.reverse(id, request, SecurityUtils.currentUserId()));
    }

    /**
     * 手动触发月结，便于联调与失败重跑；正式环境由定时任务调用。
     */
    @PostMapping("/settle")
    public ApiResponse<PerformanceSettleResultVO> settle(@RequestParam String month) {
        return ApiResponse.ok(performanceService.settle(month));
    }
}

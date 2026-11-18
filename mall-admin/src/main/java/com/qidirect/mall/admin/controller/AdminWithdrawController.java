package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.domain.dto.admin.WithdrawBatchCreateRequest;
import com.qidirect.mall.domain.dto.admin.WithdrawFailRequest;
import com.qidirect.mall.domain.dto.admin.WithdrawPaidRequest;
import com.qidirect.mall.domain.dto.admin.WithdrawRejectRequest;
import com.qidirect.mall.domain.vo.WithdrawBatchVO;
import com.qidirect.mall.domain.vo.WithdrawVO;
import com.qidirect.mall.infra.service.AdminWithdrawService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 后台提现：审核、批次生成与导出、打款成功与失败。
 */
@RestController
@RequestMapping("/withdraws")
@RequiredArgsConstructor
public class AdminWithdrawController {

    private final AdminWithdrawService adminWithdrawService;

    @GetMapping
    public ApiResponse<PageResult<WithdrawVO>> page(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long batchId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(adminWithdrawService.page(storeId, status, batchId, page, size));
    }

    @GetMapping("/batches")
    public ApiResponse<PageResult<WithdrawBatchVO>> batchPage(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(adminWithdrawService.batchPage(status, page, size));
    }

    @PostMapping("/batches")
    public ApiResponse<WithdrawBatchVO> createBatch(@RequestBody(required = false) WithdrawBatchCreateRequest request) {
        return ApiResponse.ok(adminWithdrawService.createBatch(request));
    }

    /**
     * CSV 导出：明文卡号，需导出权限并写操作日志。
     */
    @GetMapping("/batches/{id}/export")
    public void export(@PathVariable Long id, HttpServletResponse response) throws IOException {
        String csv = adminWithdrawService.exportCsv(id);
        response.setContentType("text/csv;charset=utf-8");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=withdraw_batch_" + id + ".csv");
        response.getOutputStream().write("\uFEFF".getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().write(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{id}")
    public ApiResponse<WithdrawVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(adminWithdrawService.detail(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<WithdrawVO> approve(@PathVariable Long id) {
        return ApiResponse.ok(adminWithdrawService.approve(id));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<WithdrawVO> reject(@PathVariable Long id,
                                          @Valid @RequestBody WithdrawRejectRequest request) {
        return ApiResponse.ok(adminWithdrawService.reject(id, request));
    }

    @PostMapping("/{id}/paid")
    public ApiResponse<WithdrawVO> paid(@PathVariable Long id,
                                        @Valid @RequestBody WithdrawPaidRequest request) {
        return ApiResponse.ok(adminWithdrawService.markPaid(id, request));
    }

    @PostMapping("/{id}/fail")
    public ApiResponse<WithdrawVO> fail(@PathVariable Long id,
                                        @Valid @RequestBody WithdrawFailRequest request) {
        return ApiResponse.ok(adminWithdrawService.markFail(id, request));
    }
}

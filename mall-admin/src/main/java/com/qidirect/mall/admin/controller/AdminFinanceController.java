package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.domain.dto.admin.AbnormalResolveRequest;
import com.qidirect.mall.domain.vo.AbnormalOrderVO;
import com.qidirect.mall.domain.vo.AdminPaymentNotifyVO;
import com.qidirect.mall.domain.vo.AdminPaymentVO;
import com.qidirect.mall.domain.vo.AdminWalletLogVO;
import com.qidirect.mall.domain.vo.ReconcileResultVO;
import com.qidirect.mall.infra.service.AdminAbnormalService;
import com.qidirect.mall.infra.service.AdminFinanceQueryService;
import com.qidirect.mall.infra.service.AdminReconcileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台财务：预存/佣金流水、支付流水与回调报文、对账、异常单查询与处置。
 */
@RestController
@RequestMapping("/finance")
@RequiredArgsConstructor
public class AdminFinanceController {

    private final AdminFinanceQueryService adminFinanceQueryService;
    private final AdminReconcileService adminReconcileService;
    private final AdminAbnormalService adminAbnormalService;

    @GetMapping("/wallet-logs")
    public ApiResponse<PageResult<AdminWalletLogVO>> walletLogs(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Integer account,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(adminFinanceQueryService.walletLogs(storeId, account, bizType,
                startTime, endTime, page, size));
    }

    @GetMapping("/payments")
    public ApiResponse<PageResult<AdminPaymentVO>> payments(
            @RequestParam(required = false) String payNo,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer channel,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(adminFinanceQueryService.payments(payNo, storeId, bizType, status, channel,
                startTime, endTime, page, size));
    }

    @GetMapping("/payments/{payNo}/notifies")
    public ApiResponse<List<AdminPaymentNotifyVO>> notifies(@PathVariable String payNo) {
        return ApiResponse.ok(adminFinanceQueryService.notifies(payNo));
    }

    /**
     * 对账：预存余额/佣金余额/冻结金额三条断言，差额恒为 0。
     */
    @GetMapping("/reconcile")
    public ApiResponse<ReconcileResultVO> reconcile() {
        return ApiResponse.ok(adminReconcileService.check());
    }

    @GetMapping("/abnormal-orders")
    public ApiResponse<PageResult<AbnormalOrderVO>> abnormalOrders(
            @RequestParam(required = false, defaultValue = "JOIN") String type,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(adminAbnormalService.page(type, page, size));
    }

    @PreAuthorize("@perm.has('finance:abnormal')")
    @PostMapping("/abnormal-orders/{type}/{id}/resolve")
    public ApiResponse<Void> resolveAbnormal(@PathVariable String type,
                                             @PathVariable Long id,
                                             @Valid @RequestBody AbnormalResolveRequest request) {
        adminAbnormalService.resolve(type, id, request);
        return ApiResponse.ok();
    }
}

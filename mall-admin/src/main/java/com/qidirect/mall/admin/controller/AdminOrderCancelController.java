package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.domain.dto.admin.OrderCancelAuditRequest;
import com.qidirect.mall.domain.vo.OrderCancelVO;
import com.qidirect.mall.infra.service.OrderCancelService;
import com.qidirect.mall.infra.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台订单取消审批。
 */
@RestController
@RequestMapping("/order-cancels")
@RequiredArgsConstructor
public class AdminOrderCancelController {

    private final OrderCancelService orderCancelService;

    @GetMapping
    public ApiResponse<PageResult<OrderCancelVO>> page(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(orderCancelService.page(status, page, size));
    }

    @PostMapping("/{id}/audit")
    @PreAuthorize("@perm.has('order:cancel')")
    public ApiResponse<OrderCancelVO> audit(@PathVariable Long id,
                                            @Valid @RequestBody OrderCancelAuditRequest request) {
        return ApiResponse.ok(orderCancelService.audit(id, Boolean.TRUE.equals(request.approved()),
                request.remark(), SecurityUtils.currentUserId()));
    }
}

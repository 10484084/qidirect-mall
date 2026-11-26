package com.qidirect.mall.api.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.domain.dto.store.OrderCancelApplyRequest;
import com.qidirect.mall.domain.dto.store.OrderCreateRequest;
import com.qidirect.mall.domain.vo.OrderVO;
import com.qidirect.mall.infra.security.SecurityUtils;
import com.qidirect.mall.domain.vo.OrderCancelVO;
import com.qidirect.mall.infra.service.OrderCancelService;
import com.qidirect.mall.infra.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门店端订货订单。
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderCancelService orderCancelService;

    @PostMapping
    public ApiResponse<OrderVO> create(@Valid @RequestBody OrderCreateRequest request) {
        return ApiResponse.ok(orderService.create(SecurityUtils.currentStoreId(), request));
    }

    @GetMapping
    public ApiResponse<PageResult<OrderVO>> page(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(orderService.storePage(SecurityUtils.currentStoreId(), status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.storeDetail(SecurityUtils.currentStoreId(), id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderVO> cancel(@PathVariable Long id) {
        return ApiResponse.ok(orderService.cancelForStore(SecurityUtils.currentStoreId(), id));
    }

    /**
     * 已支付且未发货的订单申请取消，等厂家审核后按支付方式退款。
     */
    @PostMapping("/{id}/cancel-request")
    public ApiResponse<OrderCancelVO> applyCancel(@PathVariable Long id,
                                                  @Valid @RequestBody(required = false) OrderCancelApplyRequest request) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.ok(orderCancelService.apply(SecurityUtils.currentStoreId(), id, reason));
    }

    @GetMapping("/{id}/cancel-request")
    public ApiResponse<OrderCancelVO> cancelRequest(@PathVariable Long id) {
        return ApiResponse.ok(orderCancelService.latestOfOrder(SecurityUtils.currentStoreId(), id));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<Void> confirm(@PathVariable Long id) {
        orderService.confirmReceipt(SecurityUtils.currentStoreId(), id);
        return ApiResponse.ok();
    }
}

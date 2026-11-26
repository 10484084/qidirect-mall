package com.qidirect.mall.admin.controller;

import com.alibaba.excel.EasyExcel;
import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.domain.dto.admin.OrderShipRequest;
import com.qidirect.mall.domain.vo.OrderVO;
import com.qidirect.mall.infra.security.SecurityUtils;
import com.qidirect.mall.infra.service.OrderService;
import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台订单查询、发货与导出。
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    public ApiResponse<PageResult<OrderVO>> page(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(orderService.adminPage(orderNo, storeId, status, startTime, endTime, page, size));
    }

    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            HttpServletResponse response) throws IOException {
        List<OrderVO> orders = orderService.adminExport(orderNo, storeId, status, startTime, endTime);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=orders.xlsx");
        EasyExcel.write(response.getOutputStream())
                .head(exportHead())
                .sheet("订单")
                .doWrite(orders.stream().map(this::exportRow).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.adminDetail(id));
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("@perm.has('order:ship')")
    public ApiResponse<OrderVO> ship(@PathVariable Long id,
                                     @Valid @RequestBody OrderShipRequest request) {
        return ApiResponse.ok(orderService.ship(id, request, SecurityUtils.currentUserId()));
    }

    private List<List<String>> exportHead() {
        return List.of(List.of("订单号"), List.of("门店ID"), List.of("门店名称"), List.of("门店手机号"),
                List.of("订单总额（分）"), List.of("预存抵扣（分）"), List.of("在线支付（分）"), List.of("状态"),
                List.of("收货人"), List.of("收货手机号"), List.of("收货地址"), List.of("物流公司"),
                List.of("物流单号"), List.of("支付单号"), List.of("创建时间"), List.of("支付时间"),
                List.of("发货时间"), List.of("完成时间"));
    }

    private List<Object> exportRow(OrderVO order) {
        return List.of(order.orderNo(), order.storeId(), text(order.storeName()), text(order.storePhone()),
                order.totalAmount(), order.depositAmount(), order.onlineAmount(), statusText(order.status()),
                order.receiverName(), order.receiverPhone(), order.receiverAddr(), text(order.shipCompany()),
                text(order.shipNo()), text(order.payNo()), text(order.createdAt()), text(order.payTime()),
                text(order.shipTime()), text(order.finishTime()));
    }

    private String statusText(Integer status) {
        return switch (status == null ? -1 : status) {
            case 0 -> "待支付";
            case 1 -> "待发货";
            case 2 -> "已发货";
            case 3 -> "已完成";
            case 4 -> "已取消";
            default -> "未知";
        };
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}

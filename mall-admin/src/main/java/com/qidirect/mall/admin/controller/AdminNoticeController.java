package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.domain.dto.admin.AdminNoticeSendRequest;
import com.qidirect.mall.domain.entity.Notice;
import com.qidirect.mall.infra.service.AdminNoticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台站内消息管理与批量发送。
 */
@RestController
@RequestMapping("/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final AdminNoticeService adminNoticeService;

    @GetMapping
    public ApiResponse<PageResult<Notice>> page(
            @RequestParam(required = false) Integer targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(adminNoticeService.page(targetType, targetId, page, size));
    }

    @PostMapping("/send")
    public ApiResponse<Integer> send(@Valid @RequestBody AdminNoticeSendRequest request) {
        return ApiResponse.ok(adminNoticeService.send(request));
    }
}

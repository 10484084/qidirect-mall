package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.domain.dto.admin.SmsTemplateSaveRequest;
import com.qidirect.mall.domain.vo.SmsTemplateVO;
import com.qidirect.mall.infra.service.AdminSmsTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台短信模板管理。
 */
@RestController
@RequestMapping("/sms-templates")
@RequiredArgsConstructor
public class AdminSmsTemplateController {

    private final AdminSmsTemplateService adminSmsTemplateService;

    @GetMapping
    public ApiResponse<List<SmsTemplateVO>> list() {
        return ApiResponse.ok(adminSmsTemplateService.list());
    }

    @PostMapping
    public ApiResponse<Void> create(@Valid @RequestBody SmsTemplateSaveRequest request) {
        adminSmsTemplateService.create(request);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @Valid @RequestBody SmsTemplateSaveRequest request) {
        adminSmsTemplateService.update(id, request);
        return ApiResponse.ok();
    }
}

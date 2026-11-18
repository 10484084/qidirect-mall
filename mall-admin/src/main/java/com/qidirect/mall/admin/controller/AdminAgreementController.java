package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.domain.dto.admin.AgreementSaveRequest;
import com.qidirect.mall.domain.vo.AgreementAdminVO;
import com.qidirect.mall.infra.service.AdminAgreementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 后台协议管理。
 */
@RestController
@RequestMapping("/agreements")
@RequiredArgsConstructor
public class AdminAgreementController {

    private final AdminAgreementService adminAgreementService;

    @GetMapping
    public ApiResponse<List<AgreementAdminVO>> list() {
        return ApiResponse.ok(adminAgreementService.list());
    }

    @PostMapping
    public ApiResponse<Void> create(@Valid @RequestBody AgreementSaveRequest request) {
        adminAgreementService.create(request);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @Valid @RequestBody AgreementSaveRequest request) {
        adminAgreementService.update(id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/status")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminAgreementService.changeStatus(id, status);
        return ApiResponse.ok();
    }
}

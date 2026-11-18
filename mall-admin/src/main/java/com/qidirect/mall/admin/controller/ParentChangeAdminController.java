package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.domain.dto.admin.ParentChangeAuditRequest;
import com.qidirect.mall.domain.entity.StoreParentChange;
import com.qidirect.mall.infra.service.AdminStoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台改上级审批。
 */
@RestController
@RequestMapping("/parent-changes")
@RequiredArgsConstructor
public class ParentChangeAdminController {

    private final AdminStoreService adminStoreService;

    @GetMapping
    public ApiResponse<List<StoreParentChange>> list(@RequestParam(required = false) Integer status) {
        return ApiResponse.ok(adminStoreService.listParentChanges(status));
    }

    @PostMapping("/{id}/audit")
    public ApiResponse<Void> audit(@PathVariable Long id,
                                   @Valid @RequestBody ParentChangeAuditRequest request) {
        adminStoreService.auditParentChange(id, request);
        return ApiResponse.ok();
    }
}

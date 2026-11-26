package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.domain.dto.admin.AdminUserSaveRequest;
import com.qidirect.mall.domain.dto.admin.AdminUserUpdateRequest;
import com.qidirect.mall.domain.vo.AdminUserVO;
import com.qidirect.mall.infra.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台管理员管理。
 */
@RestController
@RequestMapping("/admin-users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ApiResponse<PageResult<AdminUserVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(adminUserService.page(keyword, status, page, size));
    }

    @PostMapping
    @PreAuthorize("@perm.has('admin:manage')")
    public ApiResponse<Void> create(@Valid @RequestBody AdminUserSaveRequest request) {
        adminUserService.create(request);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('admin:manage')")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @Valid @RequestBody AdminUserUpdateRequest request) {
        adminUserService.update(id, request);
        return ApiResponse.ok();
    }
}

package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.domain.dto.admin.AdminRoleSaveRequest;
import com.qidirect.mall.domain.vo.AdminRoleVO;
import com.qidirect.mall.infra.service.AdminRoleService;
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
 * 后台角色管理。
 */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final AdminRoleService adminRoleService;

    @GetMapping
    public ApiResponse<List<AdminRoleVO>> list() {
        return ApiResponse.ok(adminRoleService.list());
    }

    @PostMapping
    public ApiResponse<Void> create(@Valid @RequestBody AdminRoleSaveRequest request) {
        adminRoleService.create(request);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @Valid @RequestBody AdminRoleSaveRequest request) {
        adminRoleService.update(id, request);
        return ApiResponse.ok();
    }
}

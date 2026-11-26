package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.domain.dto.admin.AdminSortRequest;
import com.qidirect.mall.domain.dto.admin.ProductSaveRequest;
import com.qidirect.mall.domain.vo.AdminProductVO;
import com.qidirect.mall.infra.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台商品管理（含规格与三档价）。
 */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    @GetMapping
    public ApiResponse<PageResult<AdminProductVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(productService.adminPage(keyword, categoryId, status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminProductVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(productService.adminDetail(id));
    }

    @PostMapping
    @PreAuthorize("@perm.has('product:edit')")
    public ApiResponse<AdminProductVO> create(@Valid @RequestBody ProductSaveRequest request) {
        return ApiResponse.ok(productService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('product:edit')")
    public ApiResponse<AdminProductVO> update(@PathVariable Long id,
                                              @Valid @RequestBody ProductSaveRequest request) {
        return ApiResponse.ok(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('product:edit')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ApiResponse.ok();
    }

    @PutMapping("/sort")
    @PreAuthorize("@perm.has('product:edit')")
    public ApiResponse<Void> sort(@Valid @RequestBody AdminSortRequest request) {
        productService.sort(request.ids());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@perm.has('product:edit')")
    public ApiResponse<Void> setStatus(@PathVariable Long id, @RequestParam Integer status) {
        productService.setStatus(id, status);
        return ApiResponse.ok();
    }
}

package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.domain.dto.admin.AdminSortRequest;
import com.qidirect.mall.domain.dto.admin.ProductCategorySaveRequest;
import com.qidirect.mall.domain.vo.ProductCategoryVO;
import com.qidirect.mall.infra.service.ProductCategoryService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台商品分类管理。
 */
@RestController
@RequestMapping("/product-categories")
@RequiredArgsConstructor
public class AdminProductCategoryController {

    private final ProductCategoryService productCategoryService;

    @GetMapping
    public ApiResponse<List<ProductCategoryVO>> list() {
        return ApiResponse.ok(productCategoryService.list(false));
    }

    @PostMapping
    @PreAuthorize("@perm.has('category:manage')")
    public ApiResponse<ProductCategoryVO> create(@Valid @RequestBody ProductCategorySaveRequest request) {
        return ApiResponse.ok(productCategoryService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('category:manage')")
    public ApiResponse<ProductCategoryVO> update(@PathVariable Long id,
                                                 @Valid @RequestBody ProductCategorySaveRequest request) {
        return ApiResponse.ok(productCategoryService.update(id, request));
    }

    @PutMapping("/sort")
    @PreAuthorize("@perm.has('category:manage')")
    public ApiResponse<Void> sort(@Valid @RequestBody AdminSortRequest request) {
        productCategoryService.sort(request.ids());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('category:manage')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productCategoryService.delete(id);
        return ApiResponse.ok();
    }
}

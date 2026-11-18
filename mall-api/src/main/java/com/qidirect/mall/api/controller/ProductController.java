package com.qidirect.mall.api.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.domain.vo.GiftRewardVO;
import com.qidirect.mall.domain.vo.ProductCategoryVO;
import com.qidirect.mall.domain.vo.StoreProductVO;
import com.qidirect.mall.infra.security.SecurityUtils;
import com.qidirect.mall.infra.service.ProductCategoryService;
import com.qidirect.mall.infra.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 门店端：分类与商品浏览。价格按当前门店档位返回，缺价规格 purchasable=false。
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductCategoryService productCategoryService;

    @GetMapping("/product-categories")
    public ApiResponse<List<ProductCategoryVO>> categories() {
        return ApiResponse.ok(productCategoryService.list(true));
    }

    @GetMapping("/products")
    public ApiResponse<List<StoreProductVO>> products(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(productService.storeList(SecurityUtils.currentStoreId(), categoryId, keyword));
    }

    @GetMapping("/products/{id}")
    public ApiResponse<StoreProductVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(productService.storeDetail(SecurityUtils.currentStoreId(), id));
    }

    @GetMapping("/products/{id}/gift-preview")
    public ApiResponse<List<GiftRewardVO>> giftPreview(@PathVariable Long id,
                                                       @RequestParam Long skuId,
                                                       @RequestParam(defaultValue = "1") Integer qty) {
        return ApiResponse.ok(productService.giftPreview(SecurityUtils.currentStoreId(), id, skuId, qty));
    }
}

package com.qidirect.mall.api.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.domain.dto.store.CartAddRequest;
import com.qidirect.mall.domain.dto.store.CartQtyRequest;
import com.qidirect.mall.domain.vo.CartItemVO;
import com.qidirect.mall.infra.security.SecurityUtils;
import com.qidirect.mall.infra.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 门店端购物车。
 */
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ApiResponse<List<CartItemVO>> list() {
        return ApiResponse.ok(cartService.list(SecurityUtils.currentStoreId()));
    }

    @PostMapping("/items")
    public ApiResponse<CartItemVO> add(@Valid @RequestBody CartAddRequest request) {
        return ApiResponse.ok(cartService.add(SecurityUtils.currentStoreId(), request));
    }

    @PutMapping("/items/{id}")
    public ApiResponse<CartItemVO> updateQty(@PathVariable Long id,
                                             @Valid @RequestBody CartQtyRequest request) {
        return ApiResponse.ok(cartService.updateQty(SecurityUtils.currentStoreId(), id, request.qty()));
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> remove(@PathVariable Long id) {
        cartService.remove(SecurityUtils.currentStoreId(), id);
        return ApiResponse.ok();
    }

    @DeleteMapping
    public ApiResponse<Void> clear() {
        cartService.clear(SecurityUtils.currentStoreId());
        return ApiResponse.ok();
    }
}

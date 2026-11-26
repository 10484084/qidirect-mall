package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.domain.dto.admin.GiftRuleSaveRequest;
import com.qidirect.mall.domain.vo.GiftRuleVO;
import com.qidirect.mall.infra.service.GiftRuleService;
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

import java.util.List;

/**
 * 后台买赠规则。启用时同事务先停用该商品旧规则，保证任一时刻仅一条 enabled=1。
 */
@RestController
@RequestMapping("/gift-rules")
@RequiredArgsConstructor
public class AdminGiftRuleController {

    private final GiftRuleService giftRuleService;

    @GetMapping
    public ApiResponse<List<GiftRuleVO>> list(@RequestParam(required = false) Long productId) {
        return ApiResponse.ok(giftRuleService.list(productId));
    }

    @PostMapping
    @PreAuthorize("@perm.has('product:edit')")
    public ApiResponse<GiftRuleVO> create(@Valid @RequestBody GiftRuleSaveRequest request) {
        return ApiResponse.ok(giftRuleService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('product:edit')")
    public ApiResponse<GiftRuleVO> update(@PathVariable Long id,
                                          @Valid @RequestBody GiftRuleSaveRequest request) {
        return ApiResponse.ok(giftRuleService.update(id, request));
    }

    @PutMapping("/{id}/enabled")
    @PreAuthorize("@perm.has('product:edit')")
    public ApiResponse<GiftRuleVO> setEnabled(@PathVariable Long id,
                                              @RequestParam boolean enabled) {
        return ApiResponse.ok(giftRuleService.setEnabled(id, enabled));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('product:edit')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        giftRuleService.delete(id);
        return ApiResponse.ok();
    }
}

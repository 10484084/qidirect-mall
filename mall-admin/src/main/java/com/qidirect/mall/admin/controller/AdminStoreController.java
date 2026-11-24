package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.domain.dto.admin.AdminParentChangeRequest;
import com.qidirect.mall.domain.dto.admin.AdminResetPhoneRequest;
import com.qidirect.mall.domain.dto.admin.AdminStoreCreateRequest;
import com.qidirect.mall.domain.dto.admin.AdminStoreStatusRequest;
import com.qidirect.mall.domain.dto.admin.BankOverrideRequest;
import com.qidirect.mall.domain.vo.AdminStoreDetailVO;
import com.qidirect.mall.domain.vo.AdminStoreNodeVO;
import com.qidirect.mall.domain.vo.AdminStoreVO;
import com.qidirect.mall.domain.vo.InviteQrVO;
import com.qidirect.mall.domain.vo.StoreEventVO;
import com.qidirect.mall.infra.service.AdminStoreService;
import com.qidirect.mall.infra.service.StoreTimelineService;
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
 * 后台门店管理：列表、树、详情、新增、重置手机号、冻结/解冻、
 * 邀请二维码、收款卡特批代改、改上级与门店时间线。
 */
@RestController
@RequestMapping("/stores")
@RequiredArgsConstructor
public class AdminStoreController {

    private final AdminStoreService adminStoreService;
    private final StoreTimelineService storeTimelineService;

    @GetMapping
    public ApiResponse<PageResult<AdminStoreVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long levelId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer authStatus,
            @RequestParam(required = false) Integer duplicateFlag,
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(adminStoreService.page(keyword, levelId, status, authStatus,
                duplicateFlag, parentId, page, size));
    }

    @GetMapping("/tree")
    public ApiResponse<List<AdminStoreNodeVO>> tree(
            @RequestParam(required = false) Long parentId) {
        return ApiResponse.ok(adminStoreService.tree(parentId));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminStoreDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(adminStoreService.detail(id));
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody AdminStoreCreateRequest request) {
        return ApiResponse.ok(adminStoreService.create(request));
    }

    @PostMapping("/{id}/reset-phone")
    public ApiResponse<Void> resetPhone(@PathVariable Long id,
                                        @Valid @RequestBody AdminResetPhoneRequest request) {
        adminStoreService.resetPhone(id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id) {
        adminStoreService.resetPassword(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/freeze")
    public ApiResponse<Void> freeze(@PathVariable Long id,
                                    @RequestBody(required = false) AdminStoreStatusRequest request) {
        adminStoreService.freeze(id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/unfreeze")
    public ApiResponse<Void> unfreeze(@PathVariable Long id,
                                      @RequestBody(required = false) AdminStoreStatusRequest request) {
        adminStoreService.unfreeze(id, request);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/invite-qr")
    public ApiResponse<InviteQrVO> inviteQr(@PathVariable Long id) {
        return ApiResponse.ok(adminStoreService.inviteQr(id));
    }

    @PostMapping("/{id}/bank")
    public ApiResponse<Void> overrideBank(@PathVariable Long id,
                                          @Valid @RequestBody BankOverrideRequest request) {
        adminStoreService.overrideBank(id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/parent")
    public ApiResponse<Void> changeParent(@PathVariable Long id,
                                          @Valid @RequestBody AdminParentChangeRequest request) {
        adminStoreService.changeParentDirect(id, request);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/events")
    public ApiResponse<PageResult<StoreEventVO>> events(
            @PathVariable Long id,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(storeTimelineService.adminPage(id, type, page, size));
    }
}

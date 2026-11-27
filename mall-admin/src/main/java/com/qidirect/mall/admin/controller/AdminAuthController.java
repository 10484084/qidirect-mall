package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.domain.dto.admin.AdminLoginRequest;
import com.qidirect.mall.domain.dto.auth.AdminResetPasswordRequest;
import com.qidirect.mall.domain.dto.auth.AdminSmsRequest;
import com.qidirect.mall.domain.dto.auth.ChangePhoneRequest;
import com.qidirect.mall.domain.vo.AdminLoginVO;
import com.qidirect.mall.infra.security.SecurityUtils;
import com.qidirect.mall.infra.service.AdminAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台管理员登录。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    /**
     * 忘记密码：发送短信验证码（手机号未绑定账号时静默成功）。
     */
    @PostMapping("/reset-code")
    public ApiResponse<Void> sendResetCode(@Valid @RequestBody AdminSmsRequest request) {
        adminAuthService.sendResetCode(request.phone());
        return ApiResponse.ok();
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody AdminResetPasswordRequest request) {
        adminAuthService.resetPassword(request.phone(), request.code(), request.newPassword());
        return ApiResponse.ok();
    }

    /** 自助修改手机号：验证码发到原绑定手机号 */
    @PostMapping("/change-phone/code")
    public ApiResponse<Void> sendChangePhoneCode() {
        adminAuthService.sendChangePhoneCode(SecurityUtils.currentUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/change-phone")
    public ApiResponse<Void> changePhone(@Valid @RequestBody ChangePhoneRequest request) {
        adminAuthService.changePhone(SecurityUtils.currentUserId(), request.code(), request.newPhone());
        return ApiResponse.ok();
    }

    @PostMapping("/login")
    public ApiResponse<AdminLoginVO> login(@Valid @RequestBody AdminLoginRequest request) {
        return ApiResponse.ok(adminAuthService.login(request));
    }
}

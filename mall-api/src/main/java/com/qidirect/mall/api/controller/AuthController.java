package com.qidirect.mall.api.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.domain.dto.auth.PwdLoginRequest;
import com.qidirect.mall.domain.dto.auth.RegisterRequest;
import com.qidirect.mall.domain.dto.auth.SmsLoginRequest;
import com.qidirect.mall.domain.dto.auth.SmsSendRequest;
import com.qidirect.mall.domain.vo.StoreLoginVO;
import com.qidirect.mall.infra.service.SmsCodeService;
import com.qidirect.mall.infra.service.StoreAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门店端认证：用户名密码登录（主）、短信验证码、注册。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SmsCodeService smsCodeService;
    private final StoreAuthService storeAuthService;

    @PostMapping("/sms")
    public ApiResponse<Void> sendSms(@Valid @RequestBody SmsSendRequest request) {
        smsCodeService.send(request.phone());
        return ApiResponse.ok();
    }

    @PostMapping("/register")
    public ApiResponse<StoreLoginVO> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(storeAuthService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<StoreLoginVO> login(@Valid @RequestBody SmsLoginRequest request) {
        return ApiResponse.ok(storeAuthService.login(request));
    }

    @PostMapping("/pwd-login")
    public ApiResponse<StoreLoginVO> pwdLogin(@Valid @RequestBody PwdLoginRequest request) {
        return ApiResponse.ok(storeAuthService.loginByUsername(request));
    }
}

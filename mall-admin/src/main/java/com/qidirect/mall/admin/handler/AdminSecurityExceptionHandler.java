package com.qidirect.mall.admin.handler;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 方法级权限校验失败：优先于全局兜底处理，返回明确的「无访问权限」而不是系统繁忙。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class AdminSecurityExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ApiResponse<Void> handleAccessDenied(AccessDeniedException e) {
        log.warn("权限不足：{}", e.getMessage());
        return ApiResponse.fail(ErrorCode.FORBIDDEN);
    }
}

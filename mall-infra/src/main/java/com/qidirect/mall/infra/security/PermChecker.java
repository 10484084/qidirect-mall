package com.qidirect.mall.infra.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 方法级权限校验：配合 @PreAuthorize("@perm.has('order:ship')") 使用。
 * 超级管理员（权限中包含 *）自动通过。
 */
@Component("perm")
public class PermChecker {

    public boolean has(String code) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(perm -> "*".equals(perm) || code.equals(perm));
    }
}

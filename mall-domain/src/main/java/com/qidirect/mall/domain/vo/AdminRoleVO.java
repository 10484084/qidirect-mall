package com.qidirect.mall.domain.vo;

import java.util.List;

/**
 * 后台角色视图，perms 为权限点列表。
 */
public record AdminRoleVO(
        Long id,
        String name,
        List<String> perms,
        Long userCount) {
}

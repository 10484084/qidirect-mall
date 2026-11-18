package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台管理员视图，带角色 ID 与名称。
 */
public record AdminUserVO(
        Long id,
        String account,
        String realName,
        Integer status,
        List<Long> roleIds,
        List<String> roleNames,
        LocalDateTime createdAt) {
}

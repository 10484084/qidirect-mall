package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;

/**
 * 后台门店列表行。
 */
public record AdminStoreVO(
        Long id,
        String name,
        String nickname,
        String phone,
        Long parentId,
        String parentName,
        String path,
        Long levelId,
        String levelName,
        Long levelPaidAmount,
        Integer status,
        Integer authStatus,
        Integer duplicateFlag,
        String inviteCode,
        LocalDateTime joinTime,
        LocalDateTime createdAt) {
}

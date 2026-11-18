package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;

/**
 * 团队成员视图（团队树按层懒加载的单个节点）。
 */
public record TeamMemberVO(
        Long storeId,
        String name,
        String phone,
        String avatar,
        Long levelId,
        String levelName,
        boolean joined,
        Integer status,
        LocalDateTime joinTime,
        long childCount) {
}

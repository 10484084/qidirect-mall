package com.qidirect.mall.domain.vo;

/**
 * 档位信息。
 */
public record StoreLevelVO(
        Long id,
        String name,
        Long fee,
        Integer sort,
        Integer upgradeDays,
        Integer longTerm,
        Integer hasBonus,
        Integer hasGift) {
}

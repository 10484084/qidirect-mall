package com.qidirect.mall.domain.vo;

/**
 * 后台门店树节点，childCount 用于前端懒加载展开。
 */
public record AdminStoreNodeVO(
        Long id,
        String name,
        String nickname,
        String phone,
        Long parentId,
        String path,
        Long levelId,
        String levelName,
        Integer status,
        long childCount) {
}

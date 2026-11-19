package com.qidirect.mall.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qidirect.mall.domain.entity.Store;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface StoreMapper extends BaseMapper<Store> {

    /**
     * 改上级生效时批量重写子孙门店的物化路径。
     * suffixStart 为旧路径长度 + 1（SUBSTRING 从 1 开始计数）。
     *
     * 注意：这里刻意不过滤逻辑删除的行（不加 deleted = 0）。物化路径是整棵子树的结构不变量，
     * 逻辑删除只应影响业务查询的可见性（由实体上的 @TableLogic 在 select 时自动过滤），
     * 不应让已删门店残留脏 path，否则任何按 path LIKE 的子树查询都可能命中它们。
     */
    @Update("UPDATE store SET path = CONCAT(#{newPrefix}, SUBSTRING(path, #{suffixStart})) "
            + "WHERE path LIKE CONCAT(#{oldPrefix}, '%') AND id <> #{storeId}")
    int updateDescendantPaths(@Param("oldPrefix") String oldPrefix,
                              @Param("newPrefix") String newPrefix,
                              @Param("suffixStart") int suffixStart,
                              @Param("storeId") Long storeId);
}

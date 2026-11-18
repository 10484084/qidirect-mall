package com.qidirect.mall.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qidirect.mall.domain.entity.Store;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface StoreMapper extends BaseMapper<Store> {

    /**
     * 改上级生效时批量重写子孙门店的物化路径。
     * suffixStart 为旧路径长度 + 1（SUBSTRING 从 1 开始计数）。
     */
    @Update("UPDATE store SET path = CONCAT(#{newPrefix}, SUBSTRING(path, #{suffixStart})) "
            + "WHERE path LIKE CONCAT(#{oldPrefix}, '%') AND id <> #{storeId} AND deleted = 0")
    int updateDescendantPaths(@Param("oldPrefix") String oldPrefix,
                              @Param("newPrefix") String newPrefix,
                              @Param("suffixStart") int suffixStart,
                              @Param("storeId") Long storeId);
}

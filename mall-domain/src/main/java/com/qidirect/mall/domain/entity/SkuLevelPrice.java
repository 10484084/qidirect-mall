package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 规格档位价。增加档位只需新增数据，不用改表结构。
 */
@Data
@TableName("sku_level_price")
public class SkuLevelPrice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long skuId;

    private Long levelId;

    /** 单价（分） */
    private Long price;
}

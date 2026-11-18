package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 商品规格。factor 为折算瓶数，用于买赠门槛计算。
 */
@Data
@TableName("product_sku")
public class ProductSku {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    /** 瓶 / 箱 */
    private String specName;

    /** 折算瓶数 */
    private Integer factor;

    private Integer sort;
}

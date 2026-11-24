package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品。每个规格配置 V1/V2/V3 三档价，未配置的档位不可购买。
 */
@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long categoryId;

    private String name;

    /** 主单位，如 瓶；规格中的包装单位按 1 包装 = N 主单位换算 */
    private String unit;

    private String cover;

    /** JSON 数组 */
    private String images;

    private String description;

    /** 是否开启 5 万档买赠 */
    private Integer v3GiftEnabled;

    private Integer recommend;

    /** 1 上架，0 下架 */
    private Integer status;

    private Integer sort;

    @TableLogic
    private Integer deleted;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}

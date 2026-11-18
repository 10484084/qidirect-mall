package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 买赠规则：同商品任一时刻仅一条 enabled=1；切换时先停用旧规则再启用新规则。
 * enabled_product 为数据库生成列，不在此映射。
 */
@Data
@TableName("gift_rule")
public class GiftRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    /** 赠品落在最小规格（瓶）的 sku */
    private Long giftSkuId;

    /** 门槛（按瓶折算） */
    private Integer thresholdQty;

    /** 赠送瓶数 */
    private Integer giftQty;

    /** 1 启用，0 停用 */
    private Integer enabled;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}

package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 加盟/补差单。同门店同类型最多一张未结单（待支付/异常），由 active_mark 唯一约束保证。
 */
@Data
@TableName("join_order")
public class JoinOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long storeId;

    private Long levelId;

    /** 1 新加盟，2 补差升级 */
    private Integer type;

    private Long beforeLevelId;

    private Long amount;

    /** 0 待支付 1 已支付 2 已取消 3 异常 4 已退款 */
    private Integer status;

    private Integer resolveType;

    private Long resolveBy;

    private LocalDateTime resolveAt;

    private LocalDateTime payTime;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}

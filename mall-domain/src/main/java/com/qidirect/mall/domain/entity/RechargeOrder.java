package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预存单。只增加预存余额，不改变门店档位。
 */
@Data
@TableName("recharge_order")
public class RechargeOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long storeId;

    /** 0 表示常驻预存 */
    private Long activityId;

    private Long levelId;

    private Long amount;

    /** 0 待支付 1 已支付 2 已取消 3 异常 4 已退款 */
    private Integer status;

    private LocalDateTime payTime;

    private Integer resolveType;

    private Long resolveBy;

    private LocalDateTime resolveAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}

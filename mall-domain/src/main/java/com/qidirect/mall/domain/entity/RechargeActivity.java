package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预存活动。限次只统计该活动下已支付的预存单，补差单不计次。
 */
@Data
@TableName("recharge_activity")
public class RechargeActivity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** 0 表示不限次 */
    private Integer limitPerStore;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 1 上架，0 下架 */
    private Integer status;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}

package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提成记录。同一业务单同一受益人只入账一次，由 uk_biz_store 唯一约束兜底。
 */
@Data
@TableName("commission_record")
public class CommissionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** JOIN/UPGRADE/RECHARGE/OFFLINE/ORDER */
    private String bizType;

    private String bizNo;

    /** 产生实收的门店 */
    private Long fromStoreId;

    /** 受益人门店 */
    private Long toStoreId;

    /** 1 一级，2 二级 */
    private Integer level;

    private BigDecimal rate;

    /** 厂家实收金额（分） */
    private Long baseAmount;

    /** 提成金额（分），向下取整 */
    private Long amount;

    /** 1 已入账，2 已冲正 */
    private Integer status;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}

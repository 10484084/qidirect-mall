package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业绩流水。月份按支付成功时间归属；owner_path 为支付时付款门店的路径快照。
 * 同一业务单号同一类型只记一次，由 uk_biz 唯一约束兜底。
 */
@Data
@TableName("performance_record")
public class PerformanceRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 付款门店 */
    private Long storeId;

    /** 支付时刻付款门店自身的 path 快照（含自己） */
    private String ownerPath;

    /** yyyy-MM */
    private String month;

    /** 金额（分），ADJUST 可为负 */
    private Long amount;

    /** JOIN/UPGRADE/RECHARGE/OFFLINE/ORDER/ADJUST */
    private String bizType;

    private String bizNo;

    /** 1系统 2人工 */
    private Integer source;

    private Long operatorId;

    private String remark;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}

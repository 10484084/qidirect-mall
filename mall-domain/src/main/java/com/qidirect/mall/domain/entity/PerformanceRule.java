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
 * 业绩奖阶梯规则：命中 min_amount 不大于业绩的最大档位，按该档位比例全额累进。
 */
@Data
@TableName("performance_rule")
public class PerformanceRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业绩门槛（分） */
    private Long minAmount;

    /** 比例，如 0.0050 表示 0.5% */
    private BigDecimal rate;

    private Integer sort;

    /** 1启用 0停用 */
    private Integer status;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}

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
 * 业绩奖记录。revision_of=0 为原始记录；冲正记录指向被冲正的记录 id。
 * performance 为结算快照，不允许直接编辑。
 */
@Data
@TableName("performance_bonus")
public class PerformanceBonus {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storeId;

    /** yyyy-MM */
    private String month;

    /** 结算快照（分） */
    private Long performance;

    private Long ruleId;

    private BigDecimal rate;

    private Long bonusAmount;

    /** 0待生成 1已入账 2已冲正 */
    private Integer status;

    private String pathSnapshot;

    /** JSON 数组，结算时子树门店 id */
    private String subtreeSnapshot;

    /** 0 原始记录，否则为被冲正记录 id */
    private Long revisionOf;

    private LocalDateTime settledAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}

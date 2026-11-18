package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 打款批次。状态：0 待打款 / 1 已打款 / 2 已结束（全部打款失败）。
 */
@Data
@TableName("withdraw_batch")
public class WithdrawBatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;

    private Long totalAmount;

    private Integer count;

    private Integer failCount;

    private Long failAmount;

    private Integer status;

    private LocalDateTime exportedAt;

    private LocalDateTime paidAt;

    /** JSON 数组，打款凭证 */
    private String proofImages;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}

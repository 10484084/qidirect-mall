package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 余额流水，只追加不修改。每次余额变动记录变动后余额与来源信息。
 */
@Data
@TableName("wallet_log")
public class WalletLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storeId;

    /** 1 预存，2 佣金 */
    private Integer account;

    /** WalletBizType 名称 */
    private String bizType;

    /** 1 入账，-1 出账 */
    private Integer direction;

    private Long amount;

    private Long balanceAfter;

    private String bizNo;

    /** 1 线上，2 线下，3 人工 */
    private Integer source;

    /** JSON */
    private String proofImages;

    private Long operatorId;

    private String remark;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}

package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 档位变更流水。预存不改变档位，from_level 与 to_level 相同。
 */
@Data
@TableName("store_level_log")
public class StoreLevelLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storeId;

    private Long fromLevel;

    private Long toLevel;

    /** JOIN / UPGRADE / RECHARGE */
    private String bizType;

    private String bizNo;

    /** 本次实付 */
    private Long amount;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}

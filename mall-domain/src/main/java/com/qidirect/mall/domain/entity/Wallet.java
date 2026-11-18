package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店钱包：预存只能订货，佣金只能提现。
 */
@Data
@TableName("wallet")
public class Wallet {

    @TableId(value = "store_id", type = IdType.INPUT)
    private Long storeId;

    private Long depositBalance;

    private Long commissionBalance;

    private Long frozenAmount;

    private Integer version;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}

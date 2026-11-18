package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 预存活动适用档位。
 */
@Data
@TableName("recharge_activity_tier")
public class RechargeActivityTier {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long levelId;
}

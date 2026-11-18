package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 档位。加盟费单位为分，档位高低按 sort 比较。
 */
@Data
@TableName("store_level")
public class StoreLevel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long fee;

    private Integer sort;

    private Integer upgradeDays;

    /** 享长期提成 */
    private Integer longTerm;

    /** 享团队业绩奖 */
    private Integer hasBonus;

    /** 享订货买赠 */
    private Integer hasGift;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}

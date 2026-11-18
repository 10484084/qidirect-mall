package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 改上级申请与审批记录。
 */
@Data
@TableName("store_parent_change")
public class StoreParentChange {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storeId;

    private Long oldParentId;

    private Long newParentId;

    private String oldPath;

    private String newPath;

    private String reason;

    /** 0待审批 1已通过 2已驳回 */
    private Integer status;

    private String applicant;

    private String approver;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}

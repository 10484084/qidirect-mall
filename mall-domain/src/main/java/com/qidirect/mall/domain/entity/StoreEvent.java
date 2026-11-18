package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店事件流水（全量留痕），只追加不修改。
 */
@Data
@TableName("store_event")
public class StoreEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storeId;

    private String eventType;

    private String bizNo;

    private String title;

    private Long amount;

    private Integer direction;

    private Integer account;

    /** JSON */
    private String detail;

    /** JSON */
    private String proofImages;

    /** 1门店 2管理员 3系统 */
    private Integer operatorType;

    private Long operatorId;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}

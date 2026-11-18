package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短信模板。code 全局唯一，provider_id 为第三方服务商模板 ID。
 */
@Data
@TableName("sms_template")
public class SmsTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String providerId;

    private String content;

    /** 1启用 0停用 */
    private Integer status;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}

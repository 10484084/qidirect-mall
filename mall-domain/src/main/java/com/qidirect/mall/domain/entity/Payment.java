package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付单。biz_type 为 JOIN/UPGRADE/RECHARGE/ORDER，channel 1 公众号 2 聚合码 3 线下。
 */
@Data
@TableName("payment")
public class Payment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String payNo;

    private String bizType;

    private Long bizId;

    private Long storeId;

    private Long amount;

    /** 1 公众号 JSAPI，2 聚合码（预留），3 线下收款 */
    private Integer channel;

    /** 线下收款方式：1 现金 2 转账 3 刷卡 */
    private Integer offlineMethod;

    /** 0 待支付 1 支付中 2 成功 3 失败 4 关闭 5 已退款 */
    private Integer status;

    private String outTradeNo;

    private LocalDateTime paidAt;

    private LocalDateTime refundAt;

    private Long refundAmount;

    private String refundNo;

    private Long refundBy;

    private LocalDateTime expireAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}

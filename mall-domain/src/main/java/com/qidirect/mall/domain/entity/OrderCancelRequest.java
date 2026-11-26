package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单取消申请：门店在发货前发起，厂家审核后按支付方式退款。
 */
@Data
@TableName("order_cancel_request")
public class OrderCancelRequest {

    /** 0 待审核 1 已通过 2 已驳回 3 退款失败 4 处理中 */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;
    public static final int STATUS_REFUND_FAILED = 3;
    public static final int STATUS_PROCESSING = 4;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String orderNo;

    private Long storeId;

    private String reason;

    private Integer status;

    private Long depositRefund;

    private Long onlineRefund;

    private String refundNo;

    private String refundMsg;

    private Long auditBy;

    private LocalDateTime auditAt;

    private String auditRemark;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}

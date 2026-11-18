package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订货订单。状态仅 0 待支付、1 待发货、2 已发货、3 已完成、4 已取消。
 */
@Data
@TableName("orders")
public class OrderInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long storeId;

    private Long totalAmount;

    private Long depositAmount;

    private Long onlineAmount;

    private Integer status;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddr;

    /** 0 未发货，1 物流，2 其他 */
    private Integer shipType;

    private String shipCompany;

    private String shipNo;

    /** JSON 数组 */
    private String shipImages;

    private String shipRemark;

    private LocalDateTime payTime;

    private LocalDateTime shipTime;

    private LocalDateTime finishTime;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}

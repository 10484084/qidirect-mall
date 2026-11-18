package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 协议签署留痕。线上勾选 sign_type=1，纸质上传 sign_type=2。
 */
@Data
@TableName("store_agreement")
public class StoreAgreement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storeId;

    private Long agreementId;

    private String version;

    private Integer signType;

    /** JSON 数组 */
    private String proofImages;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime signedAt;

    private String ip;
}

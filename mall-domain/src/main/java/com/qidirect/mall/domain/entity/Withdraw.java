package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提现申请。状态：0 待审核 / 1 待打款 / 2 已打款 / 3 已驳回 / 4 打款失败。
 * 卡号为申请时快照（AES 密文），后续改卡不影响已提交申请。
 */
@Data
@TableName("withdraw")
public class Withdraw {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storeId;

    private Long amount;

    private String bankHolder;

    private String bankName;

    /** AES 密文 */
    private String bankAccount;

    private Integer status;

    private String failReason;

    private LocalDateTime failAt;

    private Long failBy;

    private Long batchId;

    private String rejectReason;

    /** JSON 数组，打款凭证 */
    private String proofImages;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime applyTime;

    private LocalDateTime auditTime;

    private LocalDateTime paidTime;

    private Long auditAdmin;

    private Integer version;
}

package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店。path 为物化路径，始终以 / 结尾，如 /1/5/12/。
 * 身份证与银行卡号使用 AES 加密存储，hash 字段用于重复人员比对。
 */
@Data
@TableName("store")
public class Store {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上级，0=厂家 */
    private Long parentId;

    private String path;

    /** 档位，0=未加盟 */
    private Long levelId;

    /** 当前等级已付对价（分），等级变更置值不累加 */
    private Long levelPaidAmount;

    private String name;

    private String phone;

    private String nickname;

    private String avatar;

    private LocalDateTime joinTime;

    private LocalDateTime upgradeDeadline;

    private String inviteCode;

    private String wxOpenid;

    /** 1正常 2冻结 */
    private Integer status;

    private String realName;

    /** AES 密文 */
    private String idCard;

    /** SHA256(身份证 + 盐) */
    private String idCardHash;

    private String licenseNo;

    private String licenseNoHash;

    /** JSON 数组 */
    private String bizImages;

    /** 0未填写 1已填写 2已核验 */
    private Integer authStatus;

    private String authProvider;

    private String authTradeNo;

    private Integer duplicateFlag;

    private Long duplicateOf;

    private String bankHolder;

    private String bankName;

    /** AES 密文 */
    private String bankAccount;

    private Integer bankHolderApproved;

    private Long bankHolderApproveBy;

    private LocalDateTime bankHolderApproveAt;

    /** JSON 数组 */
    private String bankHolderProof;

    private String remark;

    private Integer version;

    @TableLogic
    private Integer deleted;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}

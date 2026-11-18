package com.qidirect.mall.common.enums;

/**
 * 提成记录的业务类型，落库为枚举名（与 commission_record.biz_type 一致）。
 */
public enum CommissionBizType {

    /** 首次加盟 */
    JOIN,
    /** 补差升档 */
    UPGRADE,
    /** 线上预存 */
    RECHARGE,
    /** 线下收款 */
    OFFLINE,
    /** 订货在线支付 */
    ORDER
}

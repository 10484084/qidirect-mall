package com.qidirect.mall.common.enums;

/**
 * 业绩流水的业务类型，落库为枚举名。ADJUST 为后台人工修正。
 */
public enum PerformanceBizType {

    /** 加盟 */
    JOIN,
    /** 补差升档 */
    UPGRADE,
    /** 预存 */
    RECHARGE,
    /** 线下收款 */
    OFFLINE,
    /** 订货在线支付 */
    ORDER,
    /** 人工修正 */
    ADJUST
}

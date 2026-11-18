package com.qidirect.mall.common.enums;

/**
 * 余额流水的业务类型，落库为枚举名。
 */
public enum WalletBizType {

    /** 加盟 */
    JOIN,
    /** 补差升档 */
    UPGRADE,
    /** 预存 */
    RECHARGE,
    /** 订单预存抵扣 */
    ORDER_BALANCE,
    /** 订单在线支付 */
    ORDER_ONLINE,
    /** 订单取消退回预存 */
    ORDER_REFUND,
    /** 提成 */
    COMMISSION,
    /** 业绩奖 */
    BONUS,
    /** 业绩奖冲正 */
    BONUS_REVERSAL,
    /** 提现打款成功 */
    WITHDRAW,
    /** 线下收款 */
    OFFLINE,
    /** 人工调整 */
    ADJUST
}

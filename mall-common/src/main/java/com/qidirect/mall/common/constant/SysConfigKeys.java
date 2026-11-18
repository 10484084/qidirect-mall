package com.qidirect.mall.common.constant;

/**
 * sys_config 配置键。值均为字符串存储，金额单位为分，比例以小数表示。
 */
public final class SysConfigKeys {

    /** 一级提成比例，默认 0.10 */
    public static final String COMMISSION_RATE_1 = "commission_rate_1";
    /** 二级提成比例，默认 0.03 */
    public static final String COMMISSION_RATE_2 = "commission_rate_2";
    /** 最低提现金额（分），默认 10000 */
    public static final String WITHDRAW_MIN_AMOUNT = "withdraw_min_amount";
    /** 提现开放日：15 表示每月 15 日，last 表示月末最后一天，逗号分隔 */
    public static final String WITHDRAW_DAYS = "withdraw_days";
    /** 今日是否提现开放日（1 是 / 0 否），由定时任务每日计算 */
    public static final String WITHDRAW_WINDOW_OPEN = "withdraw_window_open";
    /** 下次提现开放日 yyyy-MM-dd，由定时任务每日计算 */
    public static final String WITHDRAW_NEXT_OPEN_DATE = "withdraw_next_open_date";
    /** 订单支付超时（分钟），默认 10 */
    public static final String ORDER_TIMEOUT_MINUTES = "order_timeout_minutes";
    /** 业绩月结日，默认 1 */
    public static final String MONTH_SETTLE_DAY = "month_settle_day";
    /** 客服微信 */
    public static final String SERVICE_WECHAT = "service_wechat";
    /** 客服电话 */
    public static final String SERVICE_PHONE = "service_phone";

    private SysConfigKeys() {
    }
}

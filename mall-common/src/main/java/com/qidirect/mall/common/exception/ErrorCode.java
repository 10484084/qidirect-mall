package com.qidirect.mall.common.exception;

import lombok.Getter;

/**
 * 错误码分段：1xxxx 通用、2xxxx 门店、3xxxx 订单、4xxxx 钱包提现、5xxxx 支付。
 */
@Getter
public enum ErrorCode {

    PARAM_ERROR(10001, "参数错误"),
    UNAUTHORIZED(10002, "未登录或登录已过期"),
    FORBIDDEN(10003, "无访问权限"),
    NOT_FOUND(10004, "资源不存在"),
    LOGIN_FAILED(10005, "账号或密码错误"),
    SMS_CODE_INVALID(10006, "验证码错误或已过期"),
    SMS_TOO_FREQUENT(10007, "验证码发送过于频繁，请稍后再试"),
    SMS_SEND_FAILED(10008, "短信发送失败，请稍后重试"),
    SYSTEM_ERROR(10999, "系统繁忙，请稍后重试"),

    STORE_NOT_FOUND(20001, "门店不存在"),
    STORE_FROZEN(20002, "门店已冻结"),
    STORE_NOT_VERIFIED(20003, "请先完成实名认证"),
    STORE_NOT_JOINED(20004, "该门店尚未加盟"),
    STORE_PHONE_EXISTS(20005, "手机号已注册"),
    STORE_PHONE_NOT_FOUND(20006, "手机号未注册"),
    BANK_HOLDER_MISMATCH(20007, "持卡人姓名须与实名认证一致"),
    PARENT_CHANGE_INVALID(20008, "目标上级不合法"),

    ORDER_NOT_FOUND(30001, "订单不存在"),
    ORDER_STATUS_INVALID(30002, "当前订单状态不允许该操作"),
    PRODUCT_NOT_FOUND(30011, "商品不存在"),
    PRODUCT_OFF_SHELF(30012, "商品已下架"),
    SKU_NOT_FOUND(30013, "商品规格不存在"),
    SKU_PRICE_MISSING(30014, "当前档位不可购买该商品"),
    CART_ITEM_NOT_FOUND(30015, "购物车记录不存在"),
    PRODUCT_IN_USE(30016, "商品规格已被使用，无法删除"),
    GIFT_RULE_INVALID(30017, "买赠规则配置不合法"),

    WALLET_BALANCE_NOT_ENOUGH(40001, "余额不足"),
    WITHDRAW_AMOUNT_BELOW_MIN(40002, "提现金额低于最低限额"),
    WITHDRAW_WINDOW_CLOSED(40003, "当前不在提现申请开放时间"),
    WALLET_NOT_FOUND(40004, "钱包不存在"),
    WALLET_AMOUNT_INVALID(40005, "金额必须大于 0"),
    WALLET_FROZEN_NOT_ENOUGH(40006, "冻结金额不足"),
    WITHDRAW_BANK_NOT_BOUND(40007, "请先绑定收款卡"),
    WITHDRAW_STATUS_INVALID(40008, "当前提现状态不允许该操作"),

    PAY_FAILED(50001, "支付失败"),
    PAY_NOTIFY_INVALID(50002, "支付回调校验失败");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}

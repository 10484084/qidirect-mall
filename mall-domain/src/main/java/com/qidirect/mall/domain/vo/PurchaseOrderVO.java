package com.qidirect.mall.domain.vo;

/**
 * 购买建单结果。bizType 为 JOIN/UPGRADE/RECHARGE，amount 为应收金额。
 */
public record PurchaseOrderVO(
        String orderNo,
        String payNo,
        String bizType,
        Long levelId,
        String levelName,
        Long beforeLevelId,
        Long amount,
        Integer status) {
}

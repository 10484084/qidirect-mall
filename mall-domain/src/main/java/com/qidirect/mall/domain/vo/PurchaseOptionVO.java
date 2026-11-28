package com.qidirect.mall.domain.vo;

/**
 * 统一购买页可选项：action 为 JOIN（全额开通）/ UPGRADE（补差）/ RECHARGE（全额预存）。
 */
public record PurchaseOptionVO(
        Long levelId,
        String levelName,
        Long fee,
        String action,
        Long payAmount,
        String description) {
}

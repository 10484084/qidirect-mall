package com.qidirect.mall.domain.vo;

/**
 * 门店提成汇总（后台门店提成明细页顶部）。
 */
public record CommissionStoreDetailVO(
        Long storeId,
        String storeName,
        String levelName,
        Long totalAmount,
        Long level1Amount,
        Long level2Amount,
        Long recordCount) {
}

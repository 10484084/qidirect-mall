package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;

/**
 * 异常单：已收款但业务前置校验失败的加盟/升级/预存单。
 */
public record AbnormalOrderVO(
        String type,
        Long orderId,
        String orderNo,
        Long storeId,
        String storeName,
        Long levelId,
        String levelName,
        Long amount,
        Integer status,
        Integer resolveType,
        Long resolveBy,
        LocalDateTime resolveAt,
        String payNo,
        LocalDateTime paidAt,
        LocalDateTime createdAt) {
}

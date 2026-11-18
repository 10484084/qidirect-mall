package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门店时间线事件视图。
 */
public record StoreEventVO(
        Long id,
        Long storeId,
        String eventType,
        String category,
        String bizNo,
        String title,
        Long amount,
        Integer direction,
        Integer account,
        String detail,
        List<String> proofImages,
        Integer operatorType,
        Long operatorId,
        LocalDateTime createdAt) {
}

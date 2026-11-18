package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 上架中的预存活动及针对当前门店的档位选项。
 */
public record RechargeActivityVO(
        Long id,
        String title,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer limitPerStore,
        List<PurchaseOptionVO> options) {
}

package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门店端与后台共用订单视图；门店端只能读取自己的订单。
 */
public record OrderVO(
        Long id,
        String orderNo,
        Long storeId,
        String storeName,
        String storePhone,
        Long totalAmount,
        Long depositAmount,
        Long onlineAmount,
        Integer status,
        String receiverName,
        String receiverPhone,
        String receiverAddr,
        Integer shipType,
        String shipCompany,
        String shipNo,
        List<String> shipImages,
        String shipRemark,
        LocalDateTime payTime,
        LocalDateTime shipTime,
        LocalDateTime finishTime,
        LocalDateTime createdAt,
        String payNo,
        List<OrderItemVO> items) {
}

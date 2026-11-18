package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.Size;

/**
 * 后台冻结/解冻门店，原因写入操作日志。
 */
public record AdminStoreStatusRequest(
        @Size(max = 255, message = "原因过长")
        String reason) {
}

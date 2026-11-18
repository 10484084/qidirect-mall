package com.qidirect.mall.domain.dto.admin;

import java.util.List;

/**
 * 批次生成：ids 为空时取全部待打款且未入批次的提现。
 */
public record WithdrawBatchCreateRequest(List<Long> ids) {
}

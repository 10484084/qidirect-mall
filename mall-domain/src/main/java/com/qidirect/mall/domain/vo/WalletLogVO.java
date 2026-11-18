package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 余额流水明细。direction=1 入账，-1 出账。
 */
public record WalletLogVO(
        Long id,
        Integer account,
        String bizType,
        Integer direction,
        Long amount,
        Long balanceAfter,
        String bizNo,
        Integer source,
        List<String> proofImages,
        String remark,
        LocalDateTime createdAt) {
}

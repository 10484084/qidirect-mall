package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台门店详情：资料（敏感字段脱敏）+ 钱包 + 持卡人特批留痕。
 */
public record AdminStoreDetailVO(
        StoreProfileVO profile,
        WalletVO wallet,
        Long bankHolderApproveBy,
        LocalDateTime bankHolderApproveAt,
        List<String> bankHolderProof,
        Long duplicateOf,
        String duplicateOfStoreName,
        String remark,
        LocalDateTime createdAt) {
}

package com.qidirect.mall.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门店资料。身份证与银行卡号脱敏返回。
 */
public record StoreProfileVO(
        Long id,
        String phone,
        String nickname,
        String avatar,
        String name,
        Long parentId,
        String parentName,
        Long levelId,
        String levelName,
        Long levelPaidAmount,
        LocalDateTime joinTime,
        LocalDateTime upgradeDeadline,
        String inviteCode,
        Integer status,
        Integer authStatus,
        String realName,
        String idCardMasked,
        String licenseNo,
        List<String> bizImages,
        String bankHolder,
        String bankName,
        String bankAccountMasked,
        Integer duplicateFlag) {
}

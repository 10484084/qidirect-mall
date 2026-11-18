package com.qidirect.mall.infra.service;

import cn.hutool.json.JSONUtil;
import com.qidirect.mall.common.enums.WalletAccount;
import com.qidirect.mall.common.enums.WalletBizType;
import com.qidirect.mall.common.enums.WalletSource;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.dto.admin.BalanceAdjustRequest;
import com.qidirect.mall.infra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 后台余额调整：计入 ADJUST 流水，不计业绩与提成，必填原因。
 */
@Service
@RequiredArgsConstructor
public class AdminWalletService {

    private final WalletService walletService;
    private final StoreGuard storeGuard;

    @Transactional
    public long adjust(BalanceAdjustRequest request) {
        WalletAccount account = WalletAccount.of(request.account());
        if (account == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "账户类型不正确");
        }
        if (request.delta() == null || request.delta() == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "调整金额不能为 0");
        }
        storeGuard.requireStore(request.storeId());
        Long adminId = SecurityUtils.currentUserId();
        String proofImages = toJson(request.proofImages());
        long delta = request.delta();
        String remark = request.remark().trim();

        if (account == WalletAccount.COMMISSION) {
            return walletService.applyCommissionDelta(request.storeId(), delta, WalletBizType.ADJUST, null,
                    WalletSource.MANUAL, proofImages, adminId, remark);
        }
        if (delta > 0) {
            return walletService.creditDeposit(request.storeId(), delta, WalletBizType.ADJUST, null,
                    WalletSource.MANUAL, proofImages, adminId, remark);
        }
        return walletService.deductDeposit(request.storeId(), -delta, WalletBizType.ADJUST, null,
                WalletSource.MANUAL, proofImages, adminId, remark);
    }

    private String toJson(List<String> images) {
        return images == null || images.isEmpty() ? null : JSONUtil.toJsonStr(images);
    }
}

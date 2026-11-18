package com.qidirect.mall.infra.service;

import com.qidirect.mall.domain.entity.Wallet;
import com.qidirect.mall.domain.vo.ReconcileMismatchVO;
import com.qidirect.mall.domain.vo.ReconcileResultVO;
import com.qidirect.mall.infra.mapper.WalletLogMapper;
import com.qidirect.mall.infra.mapper.WalletMapper;
import com.qidirect.mall.infra.mapper.WithdrawMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 财务对账：三条断言必须恒成立。
 * 1) 预存余额 = 预存流水净额；2) 佣金余额 = 佣金流水净额；
 * 3) 冻结金额 = 待审核 + 待打款提现金额。
 */
@Service
@RequiredArgsConstructor
public class AdminReconcileService {

    private final WalletMapper walletMapper;
    private final WalletLogMapper walletLogMapper;
    private final WithdrawMapper withdrawMapper;

    public ReconcileResultVO check() {
        List<Wallet> wallets = walletMapper.selectList(null);
        Map<Long, Long> depositNet = netMap(walletLogMapper.sumNetByAccount(1));
        Map<Long, Long> commissionNet = netMap(walletLogMapper.sumNetByAccount(2));
        Map<Long, Long> frozen = netMap(withdrawMapper.sumFrozenByStore());

        List<ReconcileMismatchVO> depositMismatches = mismatches(wallets, Wallet::getDepositBalance, depositNet);
        List<ReconcileMismatchVO> commissionMismatches =
                mismatches(wallets, Wallet::getCommissionBalance, commissionNet);
        List<ReconcileMismatchVO> frozenMismatches = mismatches(wallets, Wallet::getFrozenAmount, frozen);

        long depositDiff = totalDiff(depositMismatches);
        long commissionDiff = totalDiff(commissionMismatches);
        long frozenDiff = totalDiff(frozenMismatches);
        boolean balanced = depositMismatches.isEmpty()
                && commissionMismatches.isEmpty()
                && frozenMismatches.isEmpty();
        return new ReconcileResultVO(balanced, depositDiff, commissionDiff, frozenDiff,
                depositMismatches, commissionMismatches, frozenMismatches, LocalDateTime.now());
    }

    private List<ReconcileMismatchVO> mismatches(List<Wallet> wallets,
                                                 Function<Wallet, Long> walletValue,
                                                 Map<Long, Long> flowValues) {
        List<ReconcileMismatchVO> result = new ArrayList<>();
        for (Wallet wallet : wallets) {
            long value = walletValue.apply(wallet) == null ? 0L : walletValue.apply(wallet);
            long flow = flowValues.getOrDefault(wallet.getStoreId(), 0L);
            if (value != flow) {
                result.add(new ReconcileMismatchVO(wallet.getStoreId(), value, flow, value - flow));
            }
        }
        return result;
    }

    private long totalDiff(List<ReconcileMismatchVO> mismatches) {
        return mismatches.stream().mapToLong(ReconcileMismatchVO::diff).sum();
    }

    private Map<Long, Long> netMap(List<Map<String, Object>> rows) {
        Map<Long, Long> result = new HashMap<>();
        if (rows == null) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Object storeId = row.get("storeId");
            Object net = row.get("net");
            if (storeId == null) {
                continue;
            }
            result.put(Long.parseLong(String.valueOf(storeId)),
                    net == null ? 0L : ((Number) net).longValue());
        }
        return result;
    }
}

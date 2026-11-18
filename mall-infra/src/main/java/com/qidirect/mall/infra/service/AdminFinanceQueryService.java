package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.domain.entity.Payment;
import com.qidirect.mall.domain.entity.PaymentNotifyLog;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.domain.entity.WalletLog;
import com.qidirect.mall.domain.vo.AdminPaymentNotifyVO;
import com.qidirect.mall.domain.vo.AdminPaymentVO;
import com.qidirect.mall.domain.vo.AdminWalletLogVO;
import com.qidirect.mall.infra.mapper.PaymentMapper;
import com.qidirect.mall.infra.mapper.PaymentNotifyLogMapper;
import com.qidirect.mall.infra.mapper.StoreMapper;
import com.qidirect.mall.infra.mapper.WalletLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后台财务查询：预存/佣金流水、支付流水与回调原始报文。
 */
@Service
@RequiredArgsConstructor
public class AdminFinanceQueryService {

    private static final long MAX_PAGE_SIZE = 100L;

    private final WalletLogMapper walletLogMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentNotifyLogMapper paymentNotifyLogMapper;
    private final StoreMapper storeMapper;

    public PageResult<AdminWalletLogVO> walletLogs(Long storeId, Integer account, String bizType,
                                                   LocalDateTime startTime, LocalDateTime endTime,
                                                   long page, long size) {
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        LambdaQueryWrapper<WalletLog> query = Wrappers.<WalletLog>lambdaQuery()
                .eq(storeId != null, WalletLog::getStoreId, storeId)
                .eq(account != null, WalletLog::getAccount, account)
                .eq(StrUtil.isNotBlank(bizType), WalletLog::getBizType, StrUtil.trimToEmpty(bizType))
                .ge(startTime != null, WalletLog::getCreatedAt, startTime)
                .le(endTime != null, WalletLog::getCreatedAt, endTime)
                .orderByDesc(WalletLog::getCreatedAt)
                .orderByDesc(WalletLog::getId);
        IPage<WalletLog> result = walletLogMapper.selectPage(new Page<>(current, pageSize), query);
        Map<Long, String> storeNames = storeNames(result.getRecords().stream()
                .map(WalletLog::getStoreId).distinct().toList());
        List<AdminWalletLogVO> records = result.getRecords().stream()
                .map(log -> new AdminWalletLogVO(log.getId(), log.getStoreId(),
                        storeNames.getOrDefault(log.getStoreId(), ""), log.getAccount(),
                        log.getBizType(), log.getDirection(), log.getAmount(), log.getBalanceAfter(),
                        log.getBizNo(), log.getSource(), log.getProofImages(), log.getOperatorId(),
                        log.getRemark(), log.getCreatedAt()))
                .toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public PageResult<AdminPaymentVO> payments(String payNo, Long storeId, String bizType, Integer status,
                                               Integer channel, LocalDateTime startTime, LocalDateTime endTime,
                                               long page, long size) {
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        LambdaQueryWrapper<Payment> query = Wrappers.<Payment>lambdaQuery()
                .like(StrUtil.isNotBlank(payNo), Payment::getPayNo, StrUtil.trimToEmpty(payNo))
                .eq(storeId != null, Payment::getStoreId, storeId)
                .eq(StrUtil.isNotBlank(bizType), Payment::getBizType, StrUtil.trimToEmpty(bizType))
                .eq(status != null, Payment::getStatus, status)
                .eq(channel != null, Payment::getChannel, channel)
                .ge(startTime != null, Payment::getCreatedAt, startTime)
                .le(endTime != null, Payment::getCreatedAt, endTime)
                .orderByDesc(Payment::getId);
        IPage<Payment> result = paymentMapper.selectPage(new Page<>(current, pageSize), query);
        Map<Long, String> storeNames = storeNames(result.getRecords().stream()
                .map(Payment::getStoreId).distinct().toList());
        List<AdminPaymentVO> records = result.getRecords().stream()
                .map(payment -> new AdminPaymentVO(payment.getId(), payment.getPayNo(),
                        payment.getBizType(), payment.getBizId(), payment.getStoreId(),
                        storeNames.getOrDefault(payment.getStoreId(), ""), payment.getAmount(),
                        payment.getChannel(), payment.getOfflineMethod(), payment.getStatus(),
                        payment.getOutTradeNo(), payment.getPaidAt(), payment.getRefundAmount(),
                        payment.getRefundNo(), payment.getRefundBy(), payment.getCreatedAt()))
                .toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 支付回调原始报文，仅后台财务可查。
     */
    public List<AdminPaymentNotifyVO> notifies(String payNo) {
        return paymentNotifyLogMapper.selectList(Wrappers.<PaymentNotifyLog>lambdaQuery()
                        .eq(PaymentNotifyLog::getPayNo, payNo)
                        .orderByDesc(PaymentNotifyLog::getId))
                .stream()
                .map(log -> new AdminPaymentNotifyVO(log.getId(), log.getPayNo(), log.getRawBody(),
                        log.getVerified(), log.getHandled(), log.getCreatedAt()))
                .toList();
    }

    private Map<Long, String> storeNames(List<Long> ids) {
        List<Long> valid = ids.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (valid.isEmpty()) {
            return Map.of();
        }
        return storeMapper.selectBatchIds(valid).stream()
                .collect(Collectors.toMap(Store::getId,
                        store -> StrUtil.blankToDefault(store.getName(),
                                StrUtil.nullToEmpty(store.getNickname()))));
    }
}

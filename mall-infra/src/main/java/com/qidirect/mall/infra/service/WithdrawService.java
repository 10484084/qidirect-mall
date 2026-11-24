package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.common.constant.SysConfigKeys;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.dto.store.WithdrawApplyRequest;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.domain.entity.StoreEvent;
import com.qidirect.mall.domain.entity.Withdraw;
import com.qidirect.mall.domain.entity.WithdrawBatch;
import com.qidirect.mall.domain.vo.WalletVO;
import com.qidirect.mall.domain.vo.WithdrawConfigVO;
import com.qidirect.mall.domain.vo.WithdrawVO;
import com.qidirect.mall.infra.mapper.StoreEventMapper;
import com.qidirect.mall.infra.mapper.StoreMapper;
import com.qidirect.mall.infra.mapper.WithdrawBatchMapper;
import com.qidirect.mall.infra.mapper.WithdrawMapper;
import com.qidirect.mall.infra.util.DataCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 门店端提现：窗口日与最低额校验、可用余额冻结、提现记录。
 */
@Service
@RequiredArgsConstructor
public class WithdrawService {

    private static final long MAX_PAGE_SIZE = 100L;

    private final WithdrawMapper withdrawMapper;
    private final WithdrawBatchMapper withdrawBatchMapper;
    private final StoreMapper storeMapper;
    private final StoreGuard storeGuard;
    private final WalletService walletService;
    private final SysConfigService sysConfigService;
    private final DataCipher dataCipher;
    private final StoreEventMapper storeEventMapper;

    public WithdrawConfigVO config(Long storeId) {
        Store store = storeGuard.requireStore(storeId);
        WalletVO wallet = walletService.getWallet(storeId);
        LocalDate today = LocalDate.now();
        boolean bankBound = hasBoundCard(store);
        return new WithdrawConfigVO(
                isOpenDay(today),
                today,
                openDays(),
                minAmount(),
                wallet.commissionBalance(),
                wallet.frozenAmount(),
                wallet.availableCommission(),
                nextOpenDate(today),
                bankBound,
                bankBound ? store.getBankHolder() : "",
                bankBound ? store.getBankName() : "",
                bankBound ? maskBankAccount(decryptQuietly(store.getBankAccount())) : "",
                bankBound ? decryptQuietly(store.getBankAccount()) : "");
    }

    /**
     * 提交提现申请：校验窗口日、最低额、收款卡与可用余额，冻结对应佣金。
     */
    @Transactional
    public WithdrawVO apply(Long storeId, WithdrawApplyRequest request) {
        Store store = storeGuard.requireStore(storeId);
        storeGuard.assertNotFrozen(store);

        long amount = request.amount();
        long minAmount = minAmount();
        if (amount < minAmount) {
            throw new BizException(ErrorCode.WITHDRAW_AMOUNT_BELOW_MIN.getCode(),
                    "提现金额低于最低限额 " + minAmount + " 分");
        }
        LocalDate today = LocalDate.now();
        if (!isOpenDay(today)) {
            throw new BizException(ErrorCode.WITHDRAW_WINDOW_CLOSED.getCode(),
                    "当前不在提现申请开放时间，可申请日期：" + openDaysText());
        }
        if (!hasBoundCard(store)) {
            throw new BizException(ErrorCode.WITHDRAW_BANK_NOT_BOUND);
        }

        walletService.freezeCommission(storeId, amount);

        Withdraw withdraw = new Withdraw();
        withdraw.setStoreId(storeId);
        withdraw.setAmount(amount);
        withdraw.setBankHolder(store.getBankHolder());
        withdraw.setBankName(store.getBankName());
        withdraw.setBankAccount(store.getBankAccount());
        withdraw.setStatus(0);
        withdraw.setBatchId(0L);
        withdraw.setRejectReason("");
        withdraw.setFailReason("");
        withdrawMapper.insert(withdraw);

        StoreEvent event = new StoreEvent();
        event.setStoreId(storeId);
        event.setEventType("WITHDRAW_APPLY");
        event.setBizNo("WD" + withdraw.getId());
        event.setTitle("提交提现申请");
        event.setAmount(amount);
        event.setDirection(-1);
        event.setAccount(2);
        event.setDetail(JSONUtil.createObj()
                .set("minAmount", minAmount)
                .set("openDays", openDays())
                .toString());
        event.setOperatorType(1);
        event.setOperatorId(storeId);
        storeEventMapper.insert(event);

        return toVO(withdrawMapper.selectById(withdraw.getId()), store, null);
    }

    public PageResult<WithdrawVO> page(Long storeId, long page, long size) {
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        IPage<Withdraw> result = withdrawMapper.selectPage(new Page<>(current, pageSize),
                Wrappers.<Withdraw>lambdaQuery()
                        .eq(Withdraw::getStoreId, storeId)
                        .orderByDesc(Withdraw::getId));
        Store store = storeMapper.selectById(storeId);
        Map<Long, WithdrawBatch> batches = batchMap(result.getRecords());
        List<WithdrawVO> records = result.getRecords().stream()
                .map(withdraw -> toVO(withdraw, store, batches.get(withdraw.getBatchId())))
                .toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 提现窗口计算：按 withdraw_days 计算今日开放状态与下次开放日，结果写回 sys_config 供后台查看。
     * 由定时任务每日调用，避免窗口配置变更后只能靠门店端请求实时推断。
     */
    public boolean refreshWindow() {
        LocalDate today = LocalDate.now();
        boolean open = isOpenDay(today);
        LocalDate next = nextOpenDate(today);
        sysConfigService.set(SysConfigKeys.WITHDRAW_WINDOW_OPEN, open ? "1" : "0",
                "今日是否提现开放日，由定时任务每日计算");
        sysConfigService.set(SysConfigKeys.WITHDRAW_NEXT_OPEN_DATE, next.toString(),
                "下次提现开放日，由定时任务每日计算");
        return open;
    }

    boolean isOpenDay(LocalDate date) {
        for (String token : openDayTokens()) {
            if ("last".equalsIgnoreCase(token)) {
                if (date.getDayOfMonth() == date.lengthOfMonth()) {
                    return true;
                }
                continue;
            }
            try {
                if (date.getDayOfMonth() == Integer.parseInt(token)) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // 非法配置项忽略
            }
        }
        return false;
    }

    private LocalDate nextOpenDate(LocalDate from) {
        for (int i = 0; i <= 366; i++) {
            LocalDate date = from.plusDays(i);
            if (isOpenDay(date)) {
                return date;
            }
        }
        return from;
    }

    private List<String> openDayTokens() {
        String config = sysConfigService.get(SysConfigKeys.WITHDRAW_DAYS, "15,last");
        return Arrays.stream(config.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .toList();
    }

    private String openDays() {
        return String.join(",", openDayTokens());
    }

    private String openDaysText() {
        return openDayTokens().stream()
                .map(token -> "last".equalsIgnoreCase(token) ? "月末" : token + "日")
                .collect(Collectors.joining("、"));
    }

    private long minAmount() {
        return Math.max(0, sysConfigService.getInt(SysConfigKeys.WITHDRAW_MIN_AMOUNT, 10000));
    }

    private boolean hasBoundCard(Store store) {
        return StrUtil.isNotBlank(store.getBankHolder())
                && StrUtil.isNotBlank(store.getBankName())
                && StrUtil.isNotBlank(store.getBankAccount());
    }

    private Map<Long, WithdrawBatch> batchMap(Collection<Withdraw> withdraws) {
        List<Long> batchIds = withdraws.stream()
                .map(Withdraw::getBatchId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (batchIds.isEmpty()) {
            return Map.of();
        }
        return withdrawBatchMapper.selectBatchIds(batchIds).stream()
                .collect(Collectors.toMap(WithdrawBatch::getId, batch -> batch));
    }

    private WithdrawVO toVO(Withdraw withdraw, Store store, WithdrawBatch batch) {
        String storeName = store == null ? "" : StrUtil.blankToDefault(store.getName(), store.getNickname());
        return new WithdrawVO(
                withdraw.getId(),
                withdraw.getStoreId(),
                StrUtil.nullToEmpty(storeName),
                store == null ? "" : StrUtil.nullToEmpty(store.getPhone()),
                withdraw.getAmount(),
                StrUtil.nullToEmpty(withdraw.getBankHolder()),
                StrUtil.nullToEmpty(withdraw.getBankName()),
                maskBankAccount(decryptQuietly(withdraw.getBankAccount())),
                withdraw.getStatus(),
                StrUtil.nullToEmpty(withdraw.getRejectReason()),
                StrUtil.nullToEmpty(withdraw.getFailReason()),
                withdraw.getFailAt(),
                withdraw.getBatchId(),
                batch == null ? "" : StrUtil.nullToEmpty(batch.getBatchNo()),
                parseImages(withdraw.getProofImages()),
                withdraw.getApplyTime(),
                withdraw.getAuditTime(),
                withdraw.getPaidTime());
    }

    private String decryptQuietly(String cipherText) {
        try {
            return dataCipher.decrypt(cipherText);
        } catch (Exception e) {
            return "";
        }
    }

    private String maskBankAccount(String account) {
        if (StrUtil.isBlank(account)) {
            return "";
        }
        if (account.length() <= 4) {
            return "****";
        }
        return "**** **** **** " + account.substring(account.length() - 4);
    }

    private List<String> parseImages(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        try {
            return JSONUtil.toList(json, String.class);
        } catch (Exception e) {
            return List.of();
        }
    }
}

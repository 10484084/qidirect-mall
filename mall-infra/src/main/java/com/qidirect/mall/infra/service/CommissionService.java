package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.common.constant.SysConfigKeys;
import com.qidirect.mall.common.enums.CommissionBizType;
import com.qidirect.mall.common.enums.WalletBizType;
import com.qidirect.mall.common.enums.WalletSource;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.dto.admin.CommissionRateRequest;
import com.qidirect.mall.domain.entity.CommissionRecord;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.domain.entity.StoreEvent;
import com.qidirect.mall.domain.entity.StoreLevel;
import com.qidirect.mall.domain.vo.CommissionConfigVO;
import com.qidirect.mall.domain.vo.CommissionRecordVO;
import com.qidirect.mall.domain.vo.CommissionStoreDetailVO;
import com.qidirect.mall.infra.mapper.CommissionRecordMapper;
import com.qidirect.mall.infra.mapper.StoreEventMapper;
import com.qidirect.mall.infra.mapper.StoreLevelMapper;
import com.qidirect.mall.infra.mapper.StoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 两级提成：按厂家实收金额计算，一级 / 二级比例后台可配，金额向下取整到分。
 * 首次加盟任意档位上级均可拿；补差、预存、线下收款、订货在线支付仅长期提成档位（5 万档）受益人可拿；
 * 未加盟门店与厂家不参与；用预存余额订货不产生提成；受益人冻结照常入账。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionService {

    private static final BigDecimal DEFAULT_RATE_1 = new BigDecimal("0.10");
    private static final BigDecimal DEFAULT_RATE_2 = new BigDecimal("0.03");
    private static final int STATUS_CREDITED = 1;
    private static final long MAX_PAGE_SIZE = 100L;

    private final CommissionRecordMapper commissionRecordMapper;
    private final StoreMapper storeMapper;
    private final StoreLevelMapper storeLevelMapper;
    private final StoreEventMapper storeEventMapper;
    private final WalletService walletService;
    private final SysConfigService sysConfigService;
    private final StoreGuard storeGuard;

    public CommissionConfigVO config() {
        return new CommissionConfigVO(rateOf(SysConfigKeys.COMMISSION_RATE_1, DEFAULT_RATE_1),
                rateOf(SysConfigKeys.COMMISSION_RATE_2, DEFAULT_RATE_2));
    }

    @Transactional
    public CommissionConfigVO updateConfig(CommissionRateRequest request) {
        validateRate(request.rate1());
        validateRate(request.rate2());
        sysConfigService.set(SysConfigKeys.COMMISSION_RATE_1, plain(request.rate1()), "一级提成比例");
        sysConfigService.set(SysConfigKeys.COMMISSION_RATE_2, plain(request.rate2()), "二级提成比例");
        return config();
    }

    public PageResult<CommissionRecordVO> page(Long storeId, Long fromStoreId, String bizType,
                                               LocalDateTime startTime, LocalDateTime endTime,
                                               long page, long size) {
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "开始时间不能晚于结束时间");
        }
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        LambdaQueryWrapper<CommissionRecord> query = Wrappers.<CommissionRecord>lambdaQuery()
                .eq(storeId != null, CommissionRecord::getToStoreId, storeId)
                .eq(fromStoreId != null, CommissionRecord::getFromStoreId, fromStoreId)
                .eq(StrUtil.isNotBlank(bizType), CommissionRecord::getBizType, bizType)
                .ge(startTime != null, CommissionRecord::getCreatedAt, startTime)
                .le(endTime != null, CommissionRecord::getCreatedAt, endTime)
                .orderByDesc(CommissionRecord::getId);
        IPage<CommissionRecord> result = commissionRecordMapper.selectPage(new Page<>(current, pageSize), query);
        List<CommissionRecordVO> records = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 门店作为受益人的提成汇总。
     */
    public CommissionStoreDetailVO storeDetail(Long storeId) {
        Store store = storeGuard.requireStore(storeId);
        List<CommissionRecord> records = commissionRecordMapper.selectList(
                Wrappers.<CommissionRecord>lambdaQuery()
                        .eq(CommissionRecord::getToStoreId, storeId)
                        .eq(CommissionRecord::getStatus, STATUS_CREDITED));
        long total = 0L;
        long level1 = 0L;
        long level2 = 0L;
        for (CommissionRecord record : records) {
            long amount = record.getAmount() == null ? 0L : record.getAmount();
            total += amount;
            if (Objects.equals(record.getLevel(), 1)) {
                level1 += amount;
            } else if (Objects.equals(record.getLevel(), 2)) {
                level2 += amount;
            }
        }
        StoreLevel level = store.getLevelId() == null || store.getLevelId() == 0
                ? null : storeLevelMapper.selectById(store.getLevelId());
        return new CommissionStoreDetailVO(storeId, store.getName(),
                level == null ? "未加盟" : level.getName(), total, level1, level2, (long) records.size());
    }

    public PageResult<CommissionRecordVO> storeRecords(Long storeId, long page, long size) {
        storeGuard.requireStore(storeId);
        return page(storeId, null, null, null, null, page, size);
    }

    /**
     * 支付成功事务内分发提成。由支付单与业务单的状态条件更新保证同一业务单只进入一次，
     * 记录表 uk_biz_store 再做一层幂等兜底。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void dispatch(CommissionBizType bizType, String bizNo, Long fromStoreId, long baseAmount) {
        if (bizType == null || StrUtil.isBlank(bizNo) || fromStoreId == null || baseAmount <= 0) {
            return;
        }
        Store from = storeMapper.selectById(fromStoreId);
        if (from == null) {
            return;
        }
        BigDecimal[] rates = {rateOf(SysConfigKeys.COMMISSION_RATE_1, DEFAULT_RATE_1),
                rateOf(SysConfigKeys.COMMISSION_RATE_2, DEFAULT_RATE_2)};
        Long ancestorId = from.getParentId();
        for (int level = 1; level <= rates.length && ancestorId != null && ancestorId > 0; level++) {
            Store beneficiary = storeMapper.selectById(ancestorId);
            if (beneficiary == null) {
                break;
            }
            if (eligible(beneficiary, bizType)) {
                credit(bizType, bizNo, from.getId(), beneficiary, level, rates[level - 1], baseAmount);
            }
            ancestorId = beneficiary.getParentId();
        }
    }

    private boolean eligible(Store beneficiary, CommissionBizType bizType) {
        Long levelId = beneficiary.getLevelId();
        if (levelId == null || levelId == 0) {
            return false;
        }
        if (bizType == CommissionBizType.JOIN) {
            return true;
        }
        StoreLevel level = storeLevelMapper.selectById(levelId);
        return level != null && Objects.equals(level.getLongTerm(), 1);
    }

    private void credit(CommissionBizType bizType, String bizNo, Long fromStoreId, Store beneficiary,
                        int level, BigDecimal rate, long baseAmount) {
        long amount = rate.multiply(BigDecimal.valueOf(baseAmount))
                .setScale(0, RoundingMode.DOWN).longValue();
        if (amount <= 0) {
            return;
        }
        Long exists = commissionRecordMapper.selectCount(Wrappers.<CommissionRecord>lambdaQuery()
                .eq(CommissionRecord::getBizType, bizType.name())
                .eq(CommissionRecord::getBizNo, bizNo)
                .eq(CommissionRecord::getToStoreId, beneficiary.getId()));
        if (exists != null && exists > 0) {
            return;
        }
        CommissionRecord record = new CommissionRecord();
        record.setBizType(bizType.name());
        record.setBizNo(bizNo);
        record.setFromStoreId(fromStoreId);
        record.setToStoreId(beneficiary.getId());
        record.setLevel(level);
        record.setRate(rate);
        record.setBaseAmount(baseAmount);
        record.setAmount(amount);
        record.setStatus(STATUS_CREDITED);
        try {
            commissionRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            log.warn("提成重复入账已忽略：bizType={} bizNo={} toStoreId={}", bizType, bizNo, beneficiary.getId());
            return;
        }
        WalletSource source = bizType == CommissionBizType.OFFLINE ? WalletSource.OFFLINE : WalletSource.ONLINE;
        walletService.creditCommission(beneficiary.getId(), amount, WalletBizType.COMMISSION, bizNo,
                source, null, 0L, "下级" + titleOf(bizType) + "（" + level + "级）");
        insertEvent(beneficiary.getId(), bizType, bizNo, fromStoreId, level, rate, amount);
    }

    private void insertEvent(Long storeId, CommissionBizType bizType, String bizNo,
                             Long fromStoreId, int level, BigDecimal rate, long amount) {
        StoreEvent event = new StoreEvent();
        event.setStoreId(storeId);
        event.setEventType("COMMISSION");
        event.setBizNo(bizNo);
        event.setTitle("下级" + titleOf(bizType));
        event.setAmount(amount);
        event.setDirection(1);
        event.setAccount(2);
        event.setDetail(JSONUtil.toJsonStr(Map.of(
                "fromStoreId", fromStoreId,
                "bizType", bizType.name(),
                "level", level,
                "rate", rate.toPlainString())));
        event.setOperatorType(3);
        event.setOperatorId(0L);
        storeEventMapper.insert(event);
    }

    private String titleOf(CommissionBizType bizType) {
        return switch (bizType) {
            case JOIN -> "加盟提成";
            case UPGRADE -> "补差提成";
            case RECHARGE -> "预存提成";
            case OFFLINE -> "线下收款提成";
            case ORDER -> "订货提成";
        };
    }

    private CommissionRecordVO toVO(CommissionRecord record) {
        Store from = storeMapper.selectById(record.getFromStoreId());
        Store to = storeMapper.selectById(record.getToStoreId());
        return new CommissionRecordVO(record.getId(), record.getBizType(), record.getBizNo(),
                record.getFromStoreId(), from == null ? "" : from.getName(),
                record.getToStoreId(), to == null ? "" : to.getName(),
                record.getLevel(), record.getRate(), record.getBaseAmount(), record.getAmount(),
                record.getStatus(), record.getCreatedAt());
    }

    private BigDecimal rateOf(String key, BigDecimal defaultValue) {
        String value = sysConfigService.get(key);
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("提成比例配置非法：key={} value={}", key, value);
            return defaultValue;
        }
    }

    private void validateRate(BigDecimal rate) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "提成比例必须在 0 到 1 之间");
        }
    }

    private String plain(BigDecimal rate) {
        return rate.stripTrailingZeros().toPlainString();
    }
}

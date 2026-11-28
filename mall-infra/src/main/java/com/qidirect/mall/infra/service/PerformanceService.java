package com.qidirect.mall.infra.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.common.enums.PerformanceBizType;
import com.qidirect.mall.common.enums.WalletBizType;
import com.qidirect.mall.common.enums.WalletSource;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.dto.admin.PerformanceAdjustRequest;
import com.qidirect.mall.domain.dto.admin.PerformanceReversalRequest;
import com.qidirect.mall.domain.dto.admin.PerformanceRuleSaveRequest;
import com.qidirect.mall.domain.entity.PerformanceBonus;
import com.qidirect.mall.domain.entity.PerformanceRecord;
import com.qidirect.mall.domain.entity.PerformanceRule;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.domain.entity.StoreEvent;
import com.qidirect.mall.domain.entity.StoreLevel;
import com.qidirect.mall.domain.entity.StoreLevelLog;
import com.qidirect.mall.domain.vo.PerformanceBonusVO;
import com.qidirect.mall.domain.vo.PerformanceRecordVO;
import com.qidirect.mall.domain.vo.PerformanceRuleVO;
import com.qidirect.mall.domain.vo.PerformanceSettleResultVO;
import com.qidirect.mall.domain.vo.PerformanceTierVO;
import com.qidirect.mall.domain.vo.StorePerformanceVO;
import com.qidirect.mall.infra.mapper.PerformanceBonusMapper;
import com.qidirect.mall.infra.mapper.PerformanceRecordMapper;
import com.qidirect.mall.infra.mapper.PerformanceRuleMapper;
import com.qidirect.mall.infra.mapper.StoreEventMapper;
import com.qidirect.mall.infra.mapper.StoreLevelLogMapper;
import com.qidirect.mall.infra.mapper.StoreLevelMapper;
import com.qidirect.mall.infra.mapper.StoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 团队业绩奖：支付成功写业绩流水；次月按上月末档位与结算时子树汇总，全额累进计算奖励。
 * 结算前修正只新增 ADJUST 流水；结算后只能通过冲正链（revision_of）修正，钱包只记差额。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int SOURCE_SYSTEM = 1;
    private static final int SOURCE_MANUAL = 2;
    private static final int STATUS_CREDITED = 1;
    private static final int STATUS_REVERSED = 2;
    private static final long MAX_PAGE_SIZE = 100L;

    private final PerformanceRecordMapper performanceRecordMapper;
    private final PerformanceRuleMapper performanceRuleMapper;
    private final PerformanceBonusMapper performanceBonusMapper;
    private final StoreMapper storeMapper;
    private final StoreLevelMapper storeLevelMapper;
    private final StoreLevelLogMapper storeLevelLogMapper;
    private final StoreEventMapper storeEventMapper;
    private final WalletService walletService;
    private final StoreGuard storeGuard;
    private final NoticeService noticeService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 支付成功事务内写业绩流水。月份按支付成功时间归属，owner_path 留存支付时路径快照。
     * uk_biz 保证同一业务单同一类型只记一次。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(PerformanceBizType bizType, String bizNo, Long storeId,
                       long amount, LocalDateTime paidAt) {
        if (bizType == null || StrUtil.isBlank(bizNo) || storeId == null || amount <= 0) {
            return;
        }
        Store store = storeMapper.selectById(storeId);
        if (store == null) {
            return;
        }
        LocalDateTime time = paidAt == null ? LocalDateTime.now() : paidAt;
        Long exists = performanceRecordMapper.selectCount(Wrappers.<PerformanceRecord>lambdaQuery()
                .eq(PerformanceRecord::getBizType, bizType.name())
                .eq(PerformanceRecord::getBizNo, bizNo));
        if (exists != null && exists > 0) {
            return;
        }
        PerformanceRecord record = new PerformanceRecord();
        record.setStoreId(storeId);
        record.setOwnerPath(StrUtil.nullToEmpty(store.getPath()));
        record.setMonth(MONTH_FORMAT.format(time));
        record.setAmount(amount);
        record.setBizType(bizType.name());
        record.setBizNo(bizNo);
        record.setSource(SOURCE_SYSTEM);
        record.setOperatorId(0L);
        record.setRemark("");
        try {
            performanceRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            log.warn("业绩流水重复写入已忽略：bizType={} bizNo={}", bizType, bizNo);
        }
    }

    public List<PerformanceRuleVO> rules() {
        return performanceRuleMapper.selectList(Wrappers.<PerformanceRule>lambdaQuery()
                        .orderByAsc(PerformanceRule::getMinAmount)
                        .orderByAsc(PerformanceRule::getId))
                .stream().map(this::toRuleVO).toList();
    }

    @Transactional
    public PerformanceRuleVO createRule(PerformanceRuleSaveRequest request) {
        PerformanceRule rule = new PerformanceRule();
        applyRule(rule, request);
        performanceRuleMapper.insert(rule);
        return toRuleVO(rule);
    }

    @Transactional
    public PerformanceRuleVO updateRule(Long id, PerformanceRuleSaveRequest request) {
        PerformanceRule rule = requireRule(id);
        applyRule(rule, request);
        performanceRuleMapper.updateById(rule);
        return toRuleVO(rule);
    }

    @Transactional
    public void deleteRule(Long id) {
        requireRule(id);
        performanceRuleMapper.deleteById(id);
    }

    /**
     * 月结：逐店事务，单店失败不影响其他门店，重跑时已结算门店跳过。
     */
    public PerformanceSettleResultVO settle(String month) {
        YearMonth yearMonth = requireMonth(month);
        LocalDateTime monthStart = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = yearMonth.atEndOfMonth().atTime(23, 59, 59);
        List<Store> stores = storeMapper.selectList(Wrappers.<Store>lambdaQuery().orderByAsc(Store::getId));
        int candidates = 0;
        int settled = 0;
        int skipped = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();
        for (Store store : stores) {
            Long levelId = levelAt(store, monthEnd);
            StoreLevel level = levelId == null || levelId == 0 ? null : storeLevelMapper.selectById(levelId);
            if (level == null || !Objects.equals(level.getHasBonus(), 1)) {
                continue;
            }
            candidates++;
            try {
                Boolean credited = transactionTemplate.execute(status ->
                        settleStore(store, yearMonth, monthStart, monthEnd));
                if (Boolean.TRUE.equals(credited)) {
                    settled++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                failures.add(store.getId() + ":" + e.getMessage());
                log.error("业绩月结失败：storeId={} month={} error={}",
                        store.getId(), yearMonth, e.toString());
            }
        }
        log.info("业绩月结完成：month={} candidates={} settled={} skipped={} failed={}",
                yearMonth, candidates, settled, skipped, failed);
        return new PerformanceSettleResultVO(yearMonth.toString(), candidates, settled, skipped,
                failed, failures);
    }

    /**
     * 单店结算：已结算跳过；月份内存在冻结区间跳过；未命中档位不写记录。
     */
    private Boolean settleStore(Store store, YearMonth yearMonth,
                                LocalDateTime monthStart, LocalDateTime monthEnd) {
        Long existing = performanceBonusMapper.selectCount(Wrappers.<PerformanceBonus>lambdaQuery()
                .eq(PerformanceBonus::getStoreId, store.getId())
                .eq(PerformanceBonus::getMonth, yearMonth.toString())
                .eq(PerformanceBonus::getRevisionOf, 0L));
        if (existing != null && existing > 0) {
            return false;
        }
        if (frozenDuring(store, monthStart, monthEnd)) {
            return false;
        }
        List<Long> subtree = subtreeStoreIds(store);
        if (subtree.isEmpty()) {
            return false;
        }
        List<PerformanceRecord> records = performanceRecordMapper.selectList(
                Wrappers.<PerformanceRecord>lambdaQuery()
                        .eq(PerformanceRecord::getMonth, yearMonth.toString())
                        .in(PerformanceRecord::getStoreId, subtree));
        long performance = 0L;
        for (PerformanceRecord record : records) {
            performance += record.getAmount() == null ? 0L : record.getAmount();
        }
        if (performance <= 0) {
            return false;
        }
        PerformanceRule rule = performanceRuleMapper.selectOne(Wrappers.<PerformanceRule>lambdaQuery()
                .eq(PerformanceRule::getStatus, 1)
                .le(PerformanceRule::getMinAmount, performance)
                .orderByDesc(PerformanceRule::getMinAmount)
                .orderByDesc(PerformanceRule::getId)
                .last("limit 1"));
        if (rule == null) {
            return false;
        }
        long bonusAmount = rule.getRate().multiply(BigDecimal.valueOf(performance))
                .setScale(0, RoundingMode.DOWN).longValue();
        PerformanceBonus bonus = new PerformanceBonus();
        bonus.setStoreId(store.getId());
        bonus.setMonth(yearMonth.toString());
        bonus.setPerformance(performance);
        bonus.setRuleId(rule.getId());
        bonus.setRate(rule.getRate());
        bonus.setBonusAmount(bonusAmount);
        bonus.setStatus(STATUS_CREDITED);
        bonus.setPathSnapshot(StrUtil.nullToEmpty(store.getPath()));
        bonus.setSubtreeSnapshot(JSONUtil.toJsonStr(subtree));
        bonus.setRevisionOf(0L);
        bonus.setSettledAt(LocalDateTime.now());
        try {
            performanceBonusMapper.insert(bonus);
        } catch (DuplicateKeyException e) {
            log.warn("业绩奖已结算，跳过：storeId={} month={}", store.getId(), yearMonth);
            return false;
        }
        String bizNo = bonusBizNo(yearMonth.toString(), store.getId());
        walletService.applyCommissionDelta(store.getId(), bonusAmount, WalletBizType.BONUS, bizNo,
                WalletSource.ONLINE, null, 0L, "团队业绩奖（" + yearMonth + "）");
        insertBonusEvent(store.getId(), "BONUS", bizNo, "团队业绩奖入账", bonusAmount, 1,
                3, 0L, Map.of("month", yearMonth.toString(), "performance", performance,
                        "ruleId", rule.getId(), "rate", rule.getRate().toPlainString()));
        noticeService.notifyStore(store.getId(), "业绩奖入账",
                "团队业绩奖（" + yearMonth + "）已入账 " + yuan(bonusAmount)
                        + "，团队业绩 " + yuan(performance),
                "BONUS", bizNo, NoticeService.SMS_BONUS_CREDITED);
        return true;
    }

    /**
     * 结算前修正：只新增一条 ADJUST 流水，原流水不变。
     */
    @Transactional
    public PerformanceRecordVO adjust(PerformanceAdjustRequest request, Long operatorId) {
        if (request.amount() == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "调整金额不能为 0");
        }
        YearMonth yearMonth = requireMonth(request.month());
        Store store = storeGuard.requireStore(request.storeId());
        Long settled = performanceBonusMapper.selectCount(Wrappers.<PerformanceBonus>lambdaQuery()
                .eq(PerformanceBonus::getStoreId, store.getId())
                .eq(PerformanceBonus::getMonth, yearMonth.toString())
                .eq(PerformanceBonus::getRevisionOf, 0L));
        if (settled != null && settled > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "该月份已结算，请使用业绩奖冲正");
        }
        PerformanceRecord record = new PerformanceRecord();
        record.setStoreId(store.getId());
        record.setOwnerPath(StrUtil.nullToEmpty(store.getPath()));
        record.setMonth(yearMonth.toString());
        record.setAmount(request.amount());
        record.setBizType(PerformanceBizType.ADJUST.name());
        record.setBizNo("A" + NO_FORMAT.format(LocalDateTime.now()) + RandomUtil.randomNumbers(4));
        record.setSource(SOURCE_MANUAL);
        record.setOperatorId(operatorId == null ? 0L : operatorId);
        record.setRemark(request.remark().trim());
        performanceRecordMapper.insert(record);
        return toRecordVO(record);
    }

    /**
     * 结算后冲正：原记录置已冲正，新增 revision_of 记录；钱包只记修正后与原始的差额。
     */
    @Transactional
    public PerformanceBonusVO reverse(Long bonusId, PerformanceReversalRequest request, Long operatorId) {
        PerformanceBonus original = performanceBonusMapper.selectById(bonusId);
        if (original == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "业绩奖记录不存在");
        }
        if (Objects.equals(original.getStatus(), STATUS_REVERSED)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "该记录已冲正");
        }
        Long revised = performanceBonusMapper.selectCount(Wrappers.<PerformanceBonus>lambdaQuery()
                .eq(PerformanceBonus::getRevisionOf, bonusId));
        if (revised != null && revised > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "该记录已存在冲正记录");
        }
        long corrected = request.correctedPerformance();
        long newBonus = original.getRate().multiply(BigDecimal.valueOf(corrected))
                .setScale(0, RoundingMode.DOWN).longValue();
        int claimed = performanceBonusMapper.update(null, Wrappers.<PerformanceBonus>lambdaUpdate()
                .eq(PerformanceBonus::getId, bonusId)
                .ne(PerformanceBonus::getStatus, STATUS_REVERSED)
                .set(PerformanceBonus::getStatus, STATUS_REVERSED));
        if (claimed == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "该记录已冲正");
        }

        Store store = storeMapper.selectById(original.getStoreId());
        List<Long> subtree = store == null ? List.of() : subtreeStoreIds(store);
        PerformanceBonus revision = new PerformanceBonus();
        revision.setStoreId(original.getStoreId());
        revision.setMonth(original.getMonth());
        revision.setPerformance(corrected);
        revision.setRuleId(original.getRuleId());
        revision.setRate(original.getRate());
        revision.setBonusAmount(newBonus);
        revision.setStatus(STATUS_CREDITED);
        revision.setPathSnapshot(StrUtil.nullToEmpty(original.getPathSnapshot()));
        revision.setSubtreeSnapshot(JSONUtil.toJsonStr(subtree));
        revision.setRevisionOf(bonusId);
        revision.setSettledAt(LocalDateTime.now());
        try {
            performanceBonusMapper.insert(revision);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "该记录已存在冲正记录");
        }

        long delta = newBonus - (original.getBonusAmount() == null ? 0L : original.getBonusAmount());
        String remark = "业绩奖冲正，原记录id=" + bonusId;
        if (StrUtil.isNotBlank(request.remark())) {
            remark += "，" + request.remark().trim();
        }
        if (delta != 0) {
            walletService.applyCommissionDelta(original.getStoreId(), delta, WalletBizType.BONUS_REVERSAL,
                    "BR-" + bonusId, WalletSource.MANUAL, null,
                    operatorId == null ? 0L : operatorId, remark);
        }
        insertBonusEvent(original.getStoreId(), "BONUS_REVERSAL", "BR-" + bonusId,
                "业绩奖冲正", Math.abs(delta), delta > 0 ? 1 : (delta < 0 ? -1 : 0),
                2, operatorId == null ? 0L : operatorId,
                Map.of("originalId", bonusId, "originalBonus", original.getBonusAmount(),
                        "correctedPerformance", corrected, "correctedBonus", newBonus,
                        "delta", delta));
        return toBonusVO(revision);
    }

    public PageResult<PerformanceRecordVO> recordPage(Long storeId, String month, String bizType,
                                                      long page, long size) {
        LambdaQueryWrapper<PerformanceRecord> query = Wrappers.<PerformanceRecord>lambdaQuery()
                .eq(storeId != null, PerformanceRecord::getStoreId, storeId)
                .eq(StrUtil.isNotBlank(month), PerformanceRecord::getMonth, month)
                .eq(StrUtil.isNotBlank(bizType), PerformanceRecord::getBizType, bizType)
                .orderByDesc(PerformanceRecord::getId);
        IPage<PerformanceRecord> result = performanceRecordMapper.selectPage(
                new Page<>(pageNo(page), pageSize(size)), query);
        List<PerformanceRecordVO> records = result.getRecords().stream().map(this::toRecordVO).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public PageResult<PerformanceBonusVO> bonusPage(Long storeId, String month, long page, long size) {
        LambdaQueryWrapper<PerformanceBonus> query = Wrappers.<PerformanceBonus>lambdaQuery()
                .eq(storeId != null, PerformanceBonus::getStoreId, storeId)
                .eq(StrUtil.isNotBlank(month), PerformanceBonus::getMonth, month)
                .orderByDesc(PerformanceBonus::getId);
        IPage<PerformanceBonus> result = performanceBonusMapper.selectPage(
                new Page<>(pageNo(page), pageSize(size)), query);
        List<PerformanceBonusVO> records = result.getRecords().stream().map(this::toBonusVO).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 门店端业绩页：团队月度业绩、阶梯进度、调整明细与本人奖励记录。
     */
    public StorePerformanceVO storePerformance(Long storeId, String month) {
        Store store = storeGuard.requireStore(storeId);
        YearMonth yearMonth = StrUtil.isBlank(month) ? YearMonth.now() : requireMonth(month);
        List<Long> subtree = subtreeStoreIds(store);
        long teamPerformance = 0L;
        long ownPerformance = 0L;
        List<PerformanceRecord> records = subtree.isEmpty() ? List.of()
                : performanceRecordMapper.selectList(Wrappers.<PerformanceRecord>lambdaQuery()
                .eq(PerformanceRecord::getMonth, yearMonth.toString())
                .in(PerformanceRecord::getStoreId, subtree));
        List<PerformanceRecord> adjustments = new ArrayList<>();
        for (PerformanceRecord record : records) {
            long amount = record.getAmount() == null ? 0L : record.getAmount();
            teamPerformance += amount;
            if (Objects.equals(record.getStoreId(), storeId)) {
                ownPerformance += amount;
            }
            if (PerformanceBizType.ADJUST.name().equals(record.getBizType())) {
                adjustments.add(record);
            }
        }
        StoreLevel level = store.getLevelId() == null || store.getLevelId() == 0
                ? null : storeLevelMapper.selectById(store.getLevelId());
        List<PerformanceRule> rules = performanceRuleMapper.selectList(
                Wrappers.<PerformanceRule>lambdaQuery()
                        .eq(PerformanceRule::getStatus, 1)
                        .orderByAsc(PerformanceRule::getMinAmount)
                        .orderByAsc(PerformanceRule::getId));
        PerformanceRule current = null;
        PerformanceRule next = null;
        List<PerformanceTierVO> tiers = new ArrayList<>();
        for (PerformanceRule rule : rules) {
            boolean reached = teamPerformance >= rule.getMinAmount();
            if (reached) {
                current = rule;
            } else if (next == null) {
                next = rule;
            }
            long gap = reached ? 0L : Math.max(rule.getMinAmount() - teamPerformance, 0L);
            tiers.add(new PerformanceTierVO(rule.getId(), rule.getMinAmount(), rule.getRate(), reached, gap));
        }
        long expectedBonus = current == null ? 0L
                : current.getRate().multiply(BigDecimal.valueOf(teamPerformance))
                .setScale(0, RoundingMode.DOWN).longValue();
        List<PerformanceBonusVO> bonuses = performanceBonusMapper.selectList(
                        Wrappers.<PerformanceBonus>lambdaQuery()
                                .eq(PerformanceBonus::getStoreId, storeId)
                                .eq(PerformanceBonus::getMonth, yearMonth.toString())
                                .orderByDesc(PerformanceBonus::getId))
                .stream().map(this::toBonusVO).toList();
        return new StorePerformanceVO(yearMonth.toString(),
                level == null ? "未开通" : level.getName(),
                level != null && Objects.equals(level.getHasBonus(), 1),
                teamPerformance, ownPerformance,
                current == null ? null : current.getMinAmount(),
                current == null ? null : current.getRate(),
                next == null ? null : next.getMinAmount(),
                next == null ? null : Math.max(next.getMinAmount() - teamPerformance, 0L),
                expectedBonus, tiers,
                adjustments.stream().map(this::toRecordVO).toList(),
                bonuses);
    }

    /**
     * 门店在结算月份内是否存在冻结区间；存在则不结算且不补发。
     */
    private boolean frozenDuring(Store store, LocalDateTime monthStart, LocalDateTime monthEnd) {
        if (Objects.equals(store.getStatus(), 2)) {
            return true;
        }
        List<StoreEvent> events = storeEventMapper.selectList(Wrappers.<StoreEvent>lambdaQuery()
                .eq(StoreEvent::getStoreId, store.getId())
                .in(StoreEvent::getEventType, "FREEZE", "UNFREEZE")
                .le(StoreEvent::getCreatedAt, monthEnd)
                .orderByAsc(StoreEvent::getCreatedAt)
                .orderByAsc(StoreEvent::getId));
        boolean frozen = false;
        for (StoreEvent event : events) {
            if ("FREEZE".equals(event.getEventType())) {
                if (!event.getCreatedAt().isBefore(monthStart)) {
                    return true;
                }
                frozen = true;
            } else if (frozen && !event.getCreatedAt().isBefore(monthStart)) {
                return true;
            } else {
                frozen = false;
            }
        }
        return frozen;
    }

    /**
     * 上月末最后一天的档位：优先取当月末之前的最后一条档位变更流水。
     */
    private Long levelAt(Store store, LocalDateTime monthEnd) {
        StoreLevelLog last = storeLevelLogMapper.selectOne(Wrappers.<StoreLevelLog>lambdaQuery()
                .eq(StoreLevelLog::getStoreId, store.getId())
                .le(StoreLevelLog::getCreatedAt, monthEnd)
                .orderByDesc(StoreLevelLog::getCreatedAt)
                .orderByDesc(StoreLevelLog::getId)
                .last("limit 1"));
        if (last != null) {
            return last.getToLevel();
        }
        if (store.getJoinTime() != null && !store.getJoinTime().isAfter(monthEnd)) {
            return store.getLevelId();
        }
        return 0L;
    }

    private List<Long> subtreeStoreIds(Store store) {
        if (StrUtil.isBlank(store.getPath())) {
            return List.of(store.getId());
        }
        return storeMapper.selectList(Wrappers.<Store>lambdaQuery()
                        .likeRight(Store::getPath, store.getPath())
                        .orderByAsc(Store::getId))
                .stream().map(Store::getId).toList();
    }

    private void insertBonusEvent(Long storeId, String eventType, String bizNo, String title,
                                  long amount, int direction, int operatorType, Long operatorId,
                                  Map<String, Object> detail) {
        StoreEvent event = new StoreEvent();
        event.setStoreId(storeId);
        event.setEventType(eventType);
        event.setBizNo(bizNo);
        event.setTitle(title);
        event.setAmount(amount);
        event.setDirection(direction);
        event.setAccount(2);
        event.setDetail(JSONUtil.toJsonStr(detail));
        event.setOperatorType(operatorType);
        event.setOperatorId(operatorId == null ? 0L : operatorId);
        try {
            storeEventMapper.insert(event);
        } catch (DuplicateKeyException e) {
            log.warn("业绩事件重复已忽略：eventType={} bizNo={} storeId={}", eventType, bizNo, storeId);
        }
    }

    private void applyRule(PerformanceRule rule, PerformanceRuleSaveRequest request) {
        rule.setMinAmount(request.minAmount());
        rule.setRate(request.rate());
        rule.setSort(request.sort() == null ? 0 : request.sort());
        rule.setStatus(request.status() == null ? 1 : request.status());
    }

    private PerformanceRule requireRule(Long id) {
        PerformanceRule rule = performanceRuleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "业绩规则不存在");
        }
        return rule;
    }

    private YearMonth requireMonth(String month) {
        if (StrUtil.isBlank(month)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "月份不能为空");
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "月份格式须为 yyyy-MM");
        }
    }

    private String bonusBizNo(String month, Long storeId) {
        return "BONUS-" + month + "-" + storeId;
    }

    private PerformanceRuleVO toRuleVO(PerformanceRule rule) {
        return new PerformanceRuleVO(rule.getId(), rule.getMinAmount(), rule.getRate(),
                rule.getSort(), rule.getStatus());
    }

    private PerformanceRecordVO toRecordVO(PerformanceRecord record) {
        Store store = storeMapper.selectById(record.getStoreId());
        return new PerformanceRecordVO(record.getId(), record.getStoreId(),
                store == null ? "" : store.getName(), record.getMonth(), record.getAmount(),
                record.getBizType(), record.getBizNo(), record.getSource(), record.getOperatorId(),
                record.getRemark(), record.getCreatedAt());
    }

    private PerformanceBonusVO toBonusVO(PerformanceBonus bonus) {
        Store store = storeMapper.selectById(bonus.getStoreId());
        return new PerformanceBonusVO(bonus.getId(), bonus.getStoreId(),
                store == null ? "" : store.getName(), bonus.getMonth(), bonus.getPerformance(),
                bonus.getRuleId(), bonus.getRate(), bonus.getBonusAmount(), bonus.getStatus(),
                bonus.getRevisionOf(), bonus.getSettledAt(), bonus.getCreatedAt());
    }

    private long pageNo(long page) {
        return Math.max(page, 1);
    }

    private long pageSize(long size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private String yuan(long amount) {
        return BigDecimal.valueOf(amount, 2).toPlainString() + " 元";
    }
}

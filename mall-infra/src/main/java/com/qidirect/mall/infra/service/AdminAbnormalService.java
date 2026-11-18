package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.common.enums.CommissionBizType;
import com.qidirect.mall.common.enums.PerformanceBizType;
import com.qidirect.mall.common.enums.WalletBizType;
import com.qidirect.mall.common.enums.WalletSource;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.dto.admin.AbnormalResolveRequest;
import com.qidirect.mall.domain.entity.JoinOrder;
import com.qidirect.mall.domain.entity.OperationLog;
import com.qidirect.mall.domain.entity.Payment;
import com.qidirect.mall.domain.entity.PerformanceBonus;
import com.qidirect.mall.domain.entity.RechargeOrder;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.domain.entity.StoreEvent;
import com.qidirect.mall.domain.entity.StoreLevel;
import com.qidirect.mall.domain.entity.StoreLevelLog;
import com.qidirect.mall.domain.vo.AbnormalOrderVO;
import com.qidirect.mall.infra.mapper.JoinOrderMapper;
import com.qidirect.mall.infra.mapper.OperationLogMapper;
import com.qidirect.mall.infra.mapper.PaymentMapper;
import com.qidirect.mall.infra.mapper.PerformanceBonusMapper;
import com.qidirect.mall.infra.mapper.RechargeOrderMapper;
import com.qidirect.mall.infra.mapper.StoreEventMapper;
import com.qidirect.mall.infra.mapper.StoreLevelLogMapper;
import com.qidirect.mall.infra.mapper.StoreLevelMapper;
import com.qidirect.mall.infra.mapper.StoreMapper;
import com.qidirect.mall.infra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 异常单处置：补开通 / 原路退款 / 仅关闭。
 * 异常单指「已收款但业务前置校验失败」的加盟、升级、预存单（status=3）。
 */
@Service
@RequiredArgsConstructor
public class AdminAbnormalService {

    private static final long MAX_PAGE_SIZE = 100L;
    private static final int STATUS_PAID = 1;
    private static final int STATUS_CANCELED = 2;
    private static final int STATUS_ABNORMAL = 3;
    private static final int STATUS_REFUNDED = 4;
    private static final int PAY_SUCCESS = 2;
    private static final int PAY_REFUNDED = 5;
    private static final int DEFAULT_UPGRADE_DAYS = 60;

    private final JoinOrderMapper joinOrderMapper;
    private final RechargeOrderMapper rechargeOrderMapper;
    private final PaymentMapper paymentMapper;
    private final StoreMapper storeMapper;
    private final StoreLevelMapper storeLevelMapper;
    private final StoreLevelLogMapper storeLevelLogMapper;
    private final StoreEventMapper storeEventMapper;
    private final PerformanceBonusMapper performanceBonusMapper;
    private final OperationLogMapper operationLogMapper;
    private final WalletService walletService;
    private final CommissionService commissionService;
    private final PerformanceService performanceService;

    public PageResult<AbnormalOrderVO> page(String type, long page, long size) {
        String normalized = normalizeType(type);
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        if ("JOIN".equals(normalized)) {
            IPage<JoinOrder> result = joinOrderMapper.selectPage(new Page<>(current, pageSize),
                    Wrappers.<JoinOrder>lambdaQuery()
                            .eq(JoinOrder::getStatus, STATUS_ABNORMAL)
                            .orderByDesc(JoinOrder::getId));
            Map<Long, Store> stores = stores(result.getRecords().stream()
                    .map(JoinOrder::getStoreId).toList());
            Map<Long, StoreLevel> levels = levels(result.getRecords().stream()
                    .map(JoinOrder::getLevelId).toList());
            Map<Long, Payment> payments = payments(result.getRecords().stream()
                    .map(JoinOrder::getId).toList(), "JOIN", "UPGRADE");
            List<AbnormalOrderVO> records = result.getRecords().stream()
                    .map(order -> toVO(order.getType() != null && order.getType() == 2 ? "UPGRADE" : "JOIN",
                            order.getId(), order.getOrderNo(), order.getStoreId(), order.getLevelId(),
                            order.getAmount(), order.getStatus(), order.getResolveType(),
                            order.getResolveBy(), order.getResolveAt(), order.getCreatedAt(),
                            stores, levels, payments))
                    .toList();
            return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
        }
        IPage<RechargeOrder> result = rechargeOrderMapper.selectPage(new Page<>(current, pageSize),
                Wrappers.<RechargeOrder>lambdaQuery()
                        .eq(RechargeOrder::getStatus, STATUS_ABNORMAL)
                        .orderByDesc(RechargeOrder::getId));
        Map<Long, Store> stores = stores(result.getRecords().stream()
                .map(RechargeOrder::getStoreId).toList());
        Map<Long, StoreLevel> levels = levels(result.getRecords().stream()
                .map(RechargeOrder::getLevelId).toList());
        Map<Long, Payment> payments = payments(result.getRecords().stream()
                .map(RechargeOrder::getId).toList(), "RECHARGE");
        List<AbnormalOrderVO> records = result.getRecords().stream()
                .map(order -> toVO("RECHARGE", order.getId(), order.getOrderNo(), order.getStoreId(),
                        order.getLevelId(), order.getAmount(), order.getStatus(), order.getResolveType(),
                        order.getResolveBy(), order.getResolveAt(), order.getCreatedAt(),
                        stores, levels, payments))
                .toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 处置异常单。所有分支先按 status=3 条件更新，避免并发重复处置。
     */
    @Transactional
    public void resolve(String type, Long id, AbnormalResolveRequest request) {
        String normalized = normalizeType(type);
        String action = StrUtil.trimToEmpty(request.action()).toUpperCase();
        if (!"REOPEN".equals(action) && !"REFUND".equals(action) && !"CLOSE".equals(action)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "处置方式仅支持 REOPEN/REFUND/CLOSE");
        }
        Long adminId = SecurityUtils.currentUserId();
        if ("JOIN".equals(normalized)) {
            resolveJoin(id, action, request, adminId);
        } else {
            resolveRecharge(id, action, request, adminId);
        }
        logOperation(adminId, "ABNORMAL_" + action, type + "#" + id, JSONUtil.createObj()
                .set("action", action)
                .set("remark", StrUtil.nullToEmpty(request.remark()))
                .toString());
    }

    private void resolveJoin(Long id, String action, AbnormalResolveRequest request, Long adminId) {
        JoinOrder order = joinOrderMapper.selectById(id);
        if (order == null || !Objects.equals(order.getStatus(), STATUS_ABNORMAL)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "异常单不存在或已处置");
        }
        Store store = requireStore(order.getStoreId());
        Payment payment = findPayment(order.getId(), "JOIN", "UPGRADE");
        if ("REOPEN".equals(action)) {
            requirePaySuccess(payment);
            reopenJoin(order, store, payment, request, adminId);
            markJoinResolved(order.getId(), STATUS_PAID, 1, adminId);
            return;
        }
        if ("REFUND".equals(action)) {
            requirePaySuccess(payment);
            refund(payment, order.getOrderNo(), order.getStoreId(), request, adminId);
            markJoinResolved(order.getId(), STATUS_REFUNDED, 2, adminId);
            return;
        }
        markJoinResolved(order.getId(), STATUS_CANCELED, 3, adminId);
    }

    private void resolveRecharge(Long id, String action, AbnormalResolveRequest request, Long adminId) {
        RechargeOrder order = rechargeOrderMapper.selectById(id);
        if (order == null || !Objects.equals(order.getStatus(), STATUS_ABNORMAL)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "异常单不存在或已处置");
        }
        Store store = requireStore(order.getStoreId());
        Payment payment = findPayment(order.getId(), "RECHARGE");
        if ("REOPEN".equals(action)) {
            requirePaySuccess(payment);
            reopenRecharge(order, store, payment, request, adminId);
            markRechargeResolved(order.getId(), STATUS_PAID, 1, adminId);
            return;
        }
        if ("REFUND".equals(action)) {
            requirePaySuccess(payment);
            refund(payment, order.getOrderNo(), order.getStoreId(), request, adminId);
            markRechargeResolved(order.getId(), STATUS_REFUNDED, 2, adminId);
            return;
        }
        markRechargeResolved(order.getId(), STATUS_CANCELED, 3, adminId);
    }

    /**
     * 补开通加盟/升级单：门店当前档位不低于目标时只入预存，否则完整补做档位变更、
     * 预存入账、提成与业绩。已结算业绩月份不允许补开通。
     */
    private void reopenJoin(JoinOrder order, Store store, Payment payment,
                            AbnormalResolveRequest request, Long adminId) {
        LocalDateTime paidAt = payment.getPaidAt() == null ? LocalDateTime.now() : payment.getPaidAt();
        assertMonthNotSettled(store.getId(), paidAt);
        StoreLevel target = storeLevelMapper.selectById(order.getLevelId());
        if (target == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "档位不存在");
        }
        StoreLevel current = store.getLevelId() == null || store.getLevelId() == 0
                ? null : storeLevelMapper.selectById(store.getLevelId());
        int currentSort = current == null || current.getSort() == null ? 0 : current.getSort();
        int targetSort = target.getSort() == null ? 0 : target.getSort();
        WalletBizType walletBiz = WalletBizType.valueOf(payment.getBizType());

        if (currentSort >= targetSort) {
            walletService.creditDeposit(store.getId(), order.getAmount(), walletBiz, order.getOrderNo(),
                    WalletSource.MANUAL, proofJson(request), adminId, remark(request));
            insertEvent(store.getId(), payment.getBizType(), order.getOrderNo(),
                    "异常单补开通：门店档位不低于目标，金额转入预存", order.getAmount(), 1, 1,
                    request, adminId);
            return;
        }

        Store patch = new Store();
        patch.setId(store.getId());
        patch.setLevelId(target.getId());
        patch.setLevelPaidAmount(target.getFee());
        if (store.getJoinTime() == null) {
            patch.setJoinTime(paidAt);
        }
        int upgradeDays = target.getUpgradeDays() == null ? DEFAULT_UPGRADE_DAYS : target.getUpgradeDays();
        patch.setUpgradeDeadline(paidAt.plusDays(upgradeDays));
        storeMapper.updateById(patch);

        insertLevelLog(store.getId(), store.getLevelId() == null ? 0L : store.getLevelId(),
                target.getId(), payment.getBizType(), order.getOrderNo(), order.getAmount());
        walletService.creditDeposit(store.getId(), order.getAmount(), walletBiz, order.getOrderNo(),
                WalletSource.MANUAL, proofJson(request), adminId, remark(request));
        insertEvent(store.getId(), payment.getBizType(), order.getOrderNo(), "异常单补开通成功",
                order.getAmount(), 1, 1, request, adminId);
        commissionService.dispatch(CommissionBizType.valueOf(payment.getBizType()),
                order.getOrderNo(), store.getId(), order.getAmount());
        performanceService.record(PerformanceBizType.valueOf(payment.getBizType()),
                order.getOrderNo(), store.getId(), order.getAmount(), paidAt);
    }

    /**
     * 补开通预存单：按原收款渠道入预存，并补记提成与业绩。
     */
    private void reopenRecharge(RechargeOrder order, Store store, Payment payment,
                                AbnormalResolveRequest request, Long adminId) {
        LocalDateTime paidAt = payment.getPaidAt() == null ? LocalDateTime.now() : payment.getPaidAt();
        assertMonthNotSettled(store.getId(), paidAt);
        boolean offline = payment.getChannel() != null && payment.getChannel() == 3;
        WalletBizType walletBiz = offline ? WalletBizType.OFFLINE : WalletBizType.RECHARGE;

        walletService.creditDeposit(store.getId(), order.getAmount(), walletBiz, order.getOrderNo(),
                WalletSource.MANUAL, proofJson(request), adminId, remark(request));
        Long levelId = store.getLevelId() == null ? 0L : store.getLevelId();
        insertLevelLog(store.getId(), levelId, levelId, walletBiz.name(), order.getOrderNo(),
                order.getAmount());
        insertEvent(store.getId(), walletBiz.name(), order.getOrderNo(), "异常单补开通成功",
                order.getAmount(), 1, 1, request, adminId);
        commissionService.dispatch(offline ? CommissionBizType.OFFLINE : CommissionBizType.RECHARGE,
                order.getOrderNo(), store.getId(), order.getAmount());
        performanceService.record(offline ? PerformanceBizType.OFFLINE : PerformanceBizType.RECHARGE,
                order.getOrderNo(), store.getId(), order.getAmount(), paidAt);
    }

    /**
     * 原路退款：只改支付单与业务单状态并写 PAY_REFUND 事件，不写余额流水。
     */
    private void refund(Payment payment, String orderNo, Long storeId, AbnormalResolveRequest request,
                        Long adminId) {
        LocalDateTime now = LocalDateTime.now();
        String refundNo = "RF" + System.currentTimeMillis();
        int updated = paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                .eq(Payment::getId, payment.getId())
                .eq(Payment::getStatus, PAY_SUCCESS)
                .set(Payment::getStatus, PAY_REFUNDED)
                .set(Payment::getRefundAt, now)
                .set(Payment::getRefundAmount, payment.getAmount())
                .set(Payment::getRefundNo, refundNo)
                .set(Payment::getRefundBy, adminId));
        if (updated == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "支付单状态已变更，无法退款");
        }
        insertEvent(storeId, "PAY_REFUND", orderNo, "异常单原路退款", payment.getAmount(),
                -1, 0, request, adminId);
    }

    private void markJoinResolved(Long orderId, int status, int resolveType, Long adminId) {
        int updated = joinOrderMapper.update(null, Wrappers.<JoinOrder>lambdaUpdate()
                .eq(JoinOrder::getId, orderId)
                .eq(JoinOrder::getStatus, STATUS_ABNORMAL)
                .set(JoinOrder::getStatus, status)
                .set(JoinOrder::getResolveType, resolveType)
                .set(JoinOrder::getResolveBy, adminId)
                .set(JoinOrder::getResolveAt, LocalDateTime.now()));
        if (updated == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "异常单已处置");
        }
    }

    private void markRechargeResolved(Long orderId, int status, int resolveType, Long adminId) {
        int updated = rechargeOrderMapper.update(null, Wrappers.<RechargeOrder>lambdaUpdate()
                .eq(RechargeOrder::getId, orderId)
                .eq(RechargeOrder::getStatus, STATUS_ABNORMAL)
                .set(RechargeOrder::getStatus, status)
                .set(RechargeOrder::getResolveType, resolveType)
                .set(RechargeOrder::getResolveBy, adminId)
                .set(RechargeOrder::getResolveAt, LocalDateTime.now()));
        if (updated == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "异常单已处置");
        }
    }

    private void assertMonthNotSettled(Long storeId, LocalDateTime paidAt) {
        String month = YearMonth.from(paidAt).toString();
        Long count = performanceBonusMapper.selectCount(Wrappers.<PerformanceBonus>lambdaQuery()
                .eq(PerformanceBonus::getStoreId, storeId)
                .eq(PerformanceBonus::getMonth, month));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "该业绩月份已结算，无法补开通，请选择原路退款或仅关闭");
        }
    }

    private void insertLevelLog(Long storeId, Long fromLevel, Long toLevel, String bizType,
                                String bizNo, Long amount) {
        StoreLevelLog log = new StoreLevelLog();
        log.setStoreId(storeId);
        log.setFromLevel(fromLevel);
        log.setToLevel(toLevel);
        log.setBizType(bizType);
        log.setBizNo(bizNo);
        log.setAmount(amount);
        storeLevelLogMapper.insert(log);
    }

    private void insertEvent(Long storeId, String eventType, String bizNo, String title, Long amount,
                             int direction, int account, AbnormalResolveRequest request, Long adminId) {
        StoreEvent event = new StoreEvent();
        event.setStoreId(storeId);
        event.setEventType(eventType);
        event.setBizNo(bizNo);
        event.setTitle(title);
        event.setAmount(amount == null ? 0L : amount);
        event.setDirection(direction);
        event.setAccount(account);
        event.setDetail(JSONUtil.createObj()
                .set("action", StrUtil.nullToEmpty(request.action()))
                .set("remark", StrUtil.nullToEmpty(request.remark()))
                .toString());
        if (request.proofImages() != null && !request.proofImages().isEmpty()) {
            event.setProofImages(JSONUtil.toJsonStr(request.proofImages()));
        }
        event.setOperatorType(2);
        event.setOperatorId(adminId);
        storeEventMapper.insert(event);
    }

    private void logOperation(Long adminId, String action, String target, String detail) {
        OperationLog log = new OperationLog();
        log.setOperator("admin#" + adminId);
        log.setModule("FINANCE");
        log.setAction(action);
        log.setTarget(target);
        log.setDetail(detail);
        log.setIp("");
        operationLogMapper.insert(log);
    }

    private Payment findPayment(Long bizId, String... bizTypes) {
        return paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getBizId, bizId)
                .in(Payment::getBizType, List.of(bizTypes))
                .in(Payment::getStatus, PAY_SUCCESS, PAY_REFUNDED)
                .orderByDesc(Payment::getId)
                .last("LIMIT 1"));
    }

    private void requirePaySuccess(Payment payment) {
        if (payment == null || !Objects.equals(payment.getStatus(), PAY_SUCCESS)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "支付单不是成功状态，无法执行该处置");
        }
    }

    private Store requireStore(Long storeId) {
        Store store = storeMapper.selectById(storeId);
        if (store == null) {
            throw new BizException(ErrorCode.STORE_NOT_FOUND);
        }
        return store;
    }

    private String normalizeType(String type) {
        String normalized = StrUtil.isBlank(type) ? "JOIN" : type.trim().toUpperCase();
        if (!"JOIN".equals(normalized) && !"RECHARGE".equals(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "异常单类型仅支持 JOIN/RECHARGE");
        }
        return normalized;
    }

    private String proofJson(AbnormalResolveRequest request) {
        return request.proofImages() == null || request.proofImages().isEmpty()
                ? null : JSONUtil.toJsonStr(request.proofImages());
    }

    private String remark(AbnormalResolveRequest request) {
        return StrUtil.blankToDefault(StrUtil.trimToEmpty(request.remark()), "异常单补开通");
    }

    private AbnormalOrderVO toVO(String type, Long orderId, String orderNo, Long storeId, Long levelId,
                                 Long amount, Integer status, Integer resolveType, Long resolveBy,
                                 LocalDateTime resolveAt, LocalDateTime createdAt,
                                 Map<Long, Store> stores, Map<Long, StoreLevel> levels,
                                 Map<Long, Payment> payments) {
        Store store = stores.get(storeId);
        StoreLevel level = levels.get(levelId);
        Payment payment = payments.get(orderId);
        return new AbnormalOrderVO(type, orderId, orderNo, storeId,
                store == null ? "" : StrUtil.blankToDefault(store.getName(),
                        StrUtil.nullToEmpty(store.getNickname())),
                levelId, level == null ? "" : level.getName(), amount, status, resolveType, resolveBy,
                resolveAt, payment == null ? "" : payment.getPayNo(),
                payment == null ? null : payment.getPaidAt(), createdAt);
    }

    private Map<Long, Store> stores(List<Long> ids) {
        List<Long> valid = ids.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (valid.isEmpty()) {
            return Map.of();
        }
        return storeMapper.selectBatchIds(valid).stream()
                .collect(Collectors.toMap(Store::getId, store -> store));
    }

    private Map<Long, StoreLevel> levels(List<Long> ids) {
        List<Long> valid = ids.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (valid.isEmpty()) {
            return Map.of();
        }
        return storeLevelMapper.selectBatchIds(valid).stream()
                .collect(Collectors.toMap(StoreLevel::getId, level -> level));
    }

    private Map<Long, Payment> payments(List<Long> bizIds, String... bizTypes) {
        List<Long> valid = bizIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (valid.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<Payment> query = Wrappers.<Payment>lambdaQuery()
                .in(Payment::getBizId, valid)
                .in(Payment::getBizType, List.of(bizTypes))
                .in(Payment::getStatus, PAY_SUCCESS, PAY_REFUNDED)
                .orderByDesc(Payment::getId);
        return paymentMapper.selectList(query).stream()
                .collect(Collectors.toMap(Payment::getBizId, payment -> payment, (a, b) -> a));
    }
}

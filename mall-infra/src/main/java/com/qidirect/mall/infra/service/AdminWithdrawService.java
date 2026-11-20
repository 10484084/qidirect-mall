package com.qidirect.mall.infra.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.dto.admin.WithdrawBatchCreateRequest;
import com.qidirect.mall.domain.dto.admin.WithdrawFailRequest;
import com.qidirect.mall.domain.dto.admin.WithdrawPaidRequest;
import com.qidirect.mall.domain.dto.admin.WithdrawRejectRequest;
import com.qidirect.mall.domain.entity.OperationLog;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.domain.entity.StoreEvent;
import com.qidirect.mall.domain.entity.Withdraw;
import com.qidirect.mall.domain.entity.WithdrawBatch;
import com.qidirect.mall.domain.vo.WithdrawBatchVO;
import com.qidirect.mall.domain.vo.WithdrawVO;
import com.qidirect.mall.infra.mapper.OperationLogMapper;
import com.qidirect.mall.infra.mapper.StoreEventMapper;
import com.qidirect.mall.infra.mapper.StoreMapper;
import com.qidirect.mall.infra.mapper.WithdrawBatchMapper;
import com.qidirect.mall.infra.mapper.WithdrawMapper;
import com.qidirect.mall.infra.security.SecurityUtils;
import com.qidirect.mall.infra.util.DataCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 后台提现：审核、批次生成与 CSV 导出、打款成功/失败。
 * 驳回与打款失败只解冻并写 store_event，不写 wallet_log；仅打款成功写 WITHDRAW 流水。
 */
@Service
@RequiredArgsConstructor
public class AdminWithdrawService {

    private static final long MAX_PAGE_SIZE = 100L;
    private static final DateTimeFormatter BATCH_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final WithdrawMapper withdrawMapper;
    private final WithdrawBatchMapper withdrawBatchMapper;
    private final StoreMapper storeMapper;
    private final StoreEventMapper storeEventMapper;
    private final OperationLogMapper operationLogMapper;
    private final WalletService walletService;
    private final NoticeService noticeService;
    private final DataCipher dataCipher;

    public PageResult<WithdrawVO> page(Long storeId, Integer status, Long batchId, long page, long size) {
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        LambdaQueryWrapper<Withdraw> query = Wrappers.<Withdraw>lambdaQuery()
                .eq(storeId != null, Withdraw::getStoreId, storeId)
                .eq(status != null, Withdraw::getStatus, status)
                .eq(batchId != null, Withdraw::getBatchId, batchId)
                .orderByDesc(Withdraw::getId);
        IPage<Withdraw> result = withdrawMapper.selectPage(new Page<>(current, pageSize), query);
        Map<Long, Store> stores = storeMap(result.getRecords().stream().map(Withdraw::getStoreId).toList());
        Map<Long, WithdrawBatch> batches = batchMap(result.getRecords());
        List<WithdrawVO> records = result.getRecords().stream()
                .map(withdraw -> toVO(withdraw, stores.get(withdraw.getStoreId()), batches.get(withdraw.getBatchId())))
                .toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public PageResult<WithdrawBatchVO> batchPage(Integer status, long page, long size) {
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        IPage<WithdrawBatch> result = withdrawBatchMapper.selectPage(new Page<>(current, pageSize),
                Wrappers.<WithdrawBatch>lambdaQuery()
                        .eq(status != null, WithdrawBatch::getStatus, status)
                        .orderByDesc(WithdrawBatch::getId));
        return PageResult.of(result.getRecords().stream().map(this::toBatchVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    public WithdrawVO detail(Long id) {
        Withdraw withdraw = requireWithdraw(id);
        Store store = storeMapper.selectById(withdraw.getStoreId());
        WithdrawBatch batch = withdraw.getBatchId() != null && withdraw.getBatchId() > 0
                ? withdrawBatchMapper.selectById(withdraw.getBatchId())
                : null;
        return toVO(withdraw, store, batch);
    }

    @Transactional
    public WithdrawVO approve(Long id) {
        Withdraw withdraw = requireWithdraw(id);
        Long adminId = SecurityUtils.currentUserId();
        LocalDateTime now = LocalDateTime.now();
        int updated = withdrawMapper.update(null, Wrappers.<Withdraw>lambdaUpdate()
                .eq(Withdraw::getId, id)
                .eq(Withdraw::getStatus, 0)
                .set(Withdraw::getStatus, 1)
                .set(Withdraw::getAuditAdmin, adminId)
                .set(Withdraw::getAuditTime, now)
                .setSql("version = version + 1"));
        if (updated == 0) {
            throw new BizException(ErrorCode.WITHDRAW_STATUS_INVALID);
        }
        insertEvent(withdraw, "WITHDRAW_AUDIT", "提现审核通过", adminId, null);
        noticeService.notifyStore(withdraw.getStoreId(), "提现审核通过",
                "提现申请 " + yuan(withdraw.getAmount()) + " 已审核通过，等待打款",
                "WITHDRAW", "WD" + id, NoticeService.SMS_WITHDRAW_RESULT);
        logOperation(adminId, "WITHDRAW_APPROVE", id, JSONUtil.createObj()
                .set("storeId", withdraw.getStoreId())
                .set("amount", withdraw.getAmount())
                .toString());
        return detail(id);
    }

    /**
     * 驳回：仅解冻并写事件，不写余额流水。
     */
    @Transactional
    public WithdrawVO reject(Long id, WithdrawRejectRequest request) {
        Withdraw withdraw = requireWithdraw(id);
        Long adminId = SecurityUtils.currentUserId();
        int updated = withdrawMapper.update(null, Wrappers.<Withdraw>lambdaUpdate()
                .eq(Withdraw::getId, id)
                .eq(Withdraw::getStatus, 0)
                .set(Withdraw::getStatus, 3)
                .set(Withdraw::getRejectReason, request.reason().trim())
                .set(Withdraw::getAuditAdmin, adminId)
                .set(Withdraw::getAuditTime, LocalDateTime.now())
                .setSql("version = version + 1"));
        if (updated == 0) {
            throw new BizException(ErrorCode.WITHDRAW_STATUS_INVALID);
        }
        walletService.unfreezeCommission(withdraw.getStoreId(), withdraw.getAmount());
        insertEvent(withdraw, "WITHDRAW_AUDIT", "提现驳回：" + request.reason().trim(), adminId, null);
        noticeService.notifyStore(withdraw.getStoreId(), "提现驳回",
                "提现申请 " + yuan(withdraw.getAmount()) + " 被驳回：" + request.reason().trim(),
                "WITHDRAW", "WD" + id, NoticeService.SMS_WITHDRAW_RESULT);
        logOperation(adminId, "WITHDRAW_REJECT", id, JSONUtil.createObj()
                .set("storeId", withdraw.getStoreId())
                .set("amount", withdraw.getAmount())
                .set("reason", request.reason().trim())
                .toString());
        return detail(id);
    }

    /**
     * 生成打款批次：只收编待打款且未入批次的提现，条件更新防并发重复入批。
     */
    @Transactional
    public WithdrawBatchVO createBatch(WithdrawBatchCreateRequest request) {
        List<Long> ids = request == null ? null : request.ids();
        LambdaQueryWrapper<Withdraw> query = Wrappers.<Withdraw>lambdaQuery()
                .eq(Withdraw::getStatus, 1)
                .eq(Withdraw::getBatchId, 0);
        if (ids != null && !ids.isEmpty()) {
            query.in(Withdraw::getId, ids);
        }
        List<Withdraw> candidates = withdrawMapper.selectList(query);
        if (candidates.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "没有可生成批次的提现");
        }

        WithdrawBatch batch = new WithdrawBatch();
        batch.setBatchNo("WB" + LocalDateTime.now().format(BATCH_NO_FORMAT) + RandomUtil.randomNumbers(4));
        batch.setTotalAmount(0L);
        batch.setCount(0);
        batch.setFailCount(0);
        batch.setFailAmount(0L);
        batch.setStatus(0);
        withdrawBatchMapper.insert(batch);

        long totalAmount = 0;
        int count = 0;
        for (Withdraw withdraw : candidates) {
            int updated = withdrawMapper.update(null, Wrappers.<Withdraw>lambdaUpdate()
                    .eq(Withdraw::getId, withdraw.getId())
                    .eq(Withdraw::getStatus, 1)
                    .eq(Withdraw::getBatchId, 0)
                    .set(Withdraw::getBatchId, batch.getId())
                    .setSql("version = version + 1"));
            if (updated > 0) {
                totalAmount += withdraw.getAmount();
                count++;
            }
        }
        if (count == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "没有可生成批次的提现");
        }
        withdrawBatchMapper.update(null, Wrappers.<WithdrawBatch>lambdaUpdate()
                .eq(WithdrawBatch::getId, batch.getId())
                .set(WithdrawBatch::getTotalAmount, totalAmount)
                .set(WithdrawBatch::getCount, count));

        logOperation(SecurityUtils.currentUserId(), "WITHDRAW_BATCH_CREATE", batch.getId(), JSONUtil.createObj()
                .set("batchNo", batch.getBatchNo())
                .set("count", count)
                .set("totalAmount", totalAmount)
                .toString());
        return toBatchVO(withdrawBatchMapper.selectById(batch.getId()));
    }

    /**
     * CSV 导出：卡号脱敏，需导出权限，并写操作日志。
     */
    @Transactional
    public String exportCsv(Long batchId) {
        if (!SecurityUtils.hasAuthority("*") && !SecurityUtils.hasAuthority("withdraw:export")) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "无提现批次导出权限");
        }
        WithdrawBatch batch = requireBatch(batchId);
        List<Withdraw> items = withdrawMapper.selectList(Wrappers.<Withdraw>lambdaQuery()
                .eq(Withdraw::getBatchId, batchId)
                .eq(Withdraw::getStatus, 1)
                .orderByAsc(Withdraw::getId));
        if (items.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "批次内没有待打款记录");
        }
        Long adminId = SecurityUtils.currentUserId();
        withdrawBatchMapper.update(null, Wrappers.<WithdrawBatch>lambdaUpdate()
                .eq(WithdrawBatch::getId, batchId)
                .set(WithdrawBatch::getExportedAt, LocalDateTime.now()));

        Map<Long, Store> stores = storeMap(items.stream().map(Withdraw::getStoreId).toList());
        StringBuilder csv = new StringBuilder();
        csv.append("门店ID,门店名称,门店手机号,持卡人,开户行,银行卡号,金额(元),申请时间\n");
        for (Withdraw withdraw : items) {
            Store store = stores.get(withdraw.getStoreId());
            csv.append(csvCell(withdraw.getStoreId())).append(',')
                    .append(csvCell(store == null ? "" : StrUtil.blankToDefault(store.getName(), store.getNickname()))).append(',')
                    .append(csvCell(store == null ? "" : store.getPhone())).append(',')
                    .append(csvCell(withdraw.getBankHolder())).append(',')
                    .append(csvCell(withdraw.getBankName())).append(',')
                    .append(csvCell(maskBankAccount(decryptQuietly(withdraw.getBankAccount())))).append(',')
                    .append(csvCell(BigDecimal.valueOf(withdraw.getAmount(), 2).toPlainString())).append(',')
                    .append(csvCell(withdraw.getApplyTime())).append('\n');
        }

        logOperation(adminId, "WITHDRAW_BATCH_EXPORT", batchId, JSONUtil.createObj()
                .set("batchNo", batch.getBatchNo())
                .set("count", items.size())
                .toString());
        return csv.toString();
    }

    @Transactional
    public WithdrawVO markPaid(Long id, WithdrawPaidRequest request) {
        Withdraw withdraw = requireWithdraw(id);
        Long adminId = SecurityUtils.currentUserId();
        String proofImages = JSONUtil.toJsonStr(request.proofImages());
        int updated = withdrawMapper.update(null, Wrappers.<Withdraw>lambdaUpdate()
                .eq(Withdraw::getId, id)
                .eq(Withdraw::getStatus, 1)
                .set(Withdraw::getStatus, 2)
                .set(Withdraw::getPaidTime, LocalDateTime.now())
                .set(Withdraw::getProofImages, proofImages)
                .setSql("version = version + 1"));
        if (updated == 0) {
            throw new BizException(ErrorCode.WITHDRAW_STATUS_INVALID);
        }
        walletService.completeWithdraw(withdraw.getStoreId(), withdraw.getAmount(), "WD" + id, proofImages, adminId);
        insertEvent(withdraw, "WITHDRAW_PAID", "提现打款成功", adminId, proofImages);
        noticeService.notifyStore(withdraw.getStoreId(), "提现打款成功",
                "提现 " + yuan(withdraw.getAmount()) + " 已打款成功",
                "WITHDRAW", "WD" + id, NoticeService.SMS_WITHDRAW_RESULT);
        recalcBatch(withdraw.getBatchId());
        logOperation(adminId, "WITHDRAW_PAID", id, JSONUtil.createObj()
                .set("storeId", withdraw.getStoreId())
                .set("amount", withdraw.getAmount())
                .toString());
        return detail(id);
    }

    /**
     * 打款失败：解冻退回可用余额，不写余额流水。
     */
    @Transactional
    public WithdrawVO markFail(Long id, WithdrawFailRequest request) {
        Withdraw withdraw = requireWithdraw(id);
        Long adminId = SecurityUtils.currentUserId();
        String reason = request.reason().trim();
        int updated = withdrawMapper.update(null, Wrappers.<Withdraw>lambdaUpdate()
                .eq(Withdraw::getId, id)
                .eq(Withdraw::getStatus, 1)
                .set(Withdraw::getStatus, 4)
                .set(Withdraw::getFailReason, reason)
                .set(Withdraw::getFailAt, LocalDateTime.now())
                .set(Withdraw::getFailBy, adminId)
                .setSql("version = version + 1"));
        if (updated == 0) {
            throw new BizException(ErrorCode.WITHDRAW_STATUS_INVALID);
        }
        walletService.unfreezeCommission(withdraw.getStoreId(), withdraw.getAmount());
        insertEvent(withdraw, "WITHDRAW_FAIL", "打款失败：" + reason, adminId, null);
        noticeService.notifyStore(withdraw.getStoreId(), "提现打款失败",
                "提现 " + yuan(withdraw.getAmount()) + " 打款失败：" + reason + "，金额已解冻",
                "WITHDRAW", "WD" + id, NoticeService.SMS_WITHDRAW_FAIL);
        recalcBatch(withdraw.getBatchId());
        logOperation(adminId, "WITHDRAW_FAIL", id, JSONUtil.createObj()
                .set("storeId", withdraw.getStoreId())
                .set("amount", withdraw.getAmount())
                .set("reason", reason)
                .toString());
        return detail(id);
    }

    /**
     * 超期未打款批次：供定时任务告警。
     */
    public List<WithdrawBatchVO> overdueBatches(int days) {
        return withdrawBatchMapper.selectList(Wrappers.<WithdrawBatch>lambdaQuery()
                        .eq(WithdrawBatch::getStatus, 0)
                        .le(WithdrawBatch::getCreatedAt, LocalDateTime.now().minusDays(days))
                        .orderByAsc(WithdrawBatch::getId))
                .stream().map(this::toBatchVO).toList();
    }

    private void recalcBatch(Long batchId) {
        if (batchId == null || batchId <= 0) {
            return;
        }
        List<Withdraw> all = withdrawMapper.selectList(Wrappers.<Withdraw>lambdaQuery()
                .eq(Withdraw::getBatchId, batchId));
        if (all.isEmpty()) {
            return;
        }
        int failCount = (int) all.stream().filter(item -> Objects.equals(item.getStatus(), 4)).count();
        long failAmount = all.stream()
                .filter(item -> Objects.equals(item.getStatus(), 4))
                .mapToLong(Withdraw::getAmount)
                .sum();
        boolean hasPending = all.stream().anyMatch(item -> Objects.equals(item.getStatus(), 1));
        boolean hasPaid = all.stream().anyMatch(item -> Objects.equals(item.getStatus(), 2));

        WithdrawBatch patch = new WithdrawBatch();
        patch.setId(batchId);
        patch.setFailCount(failCount);
        patch.setFailAmount(failAmount);
        if (!hasPending) {
            patch.setStatus(hasPaid ? 1 : 2);
            patch.setPaidAt(LocalDateTime.now());
        }
        withdrawBatchMapper.updateById(patch);
    }

    private Withdraw requireWithdraw(Long id) {
        Withdraw withdraw = withdrawMapper.selectById(id);
        if (withdraw == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "提现记录不存在");
        }
        return withdraw;
    }

    private WithdrawBatch requireBatch(Long id) {
        WithdrawBatch batch = withdrawBatchMapper.selectById(id);
        if (batch == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "打款批次不存在");
        }
        return batch;
    }

    private void insertEvent(Withdraw withdraw, String eventType, String title,
                             Long adminId, String proofImages) {
        StoreEvent event = new StoreEvent();
        event.setStoreId(withdraw.getStoreId());
        event.setEventType(eventType);
        event.setBizNo("WD" + withdraw.getId());
        event.setTitle(title);
        event.setAmount(withdraw.getAmount());
        event.setDirection(0);
        event.setAccount(2);
        event.setDetail(JSONUtil.createObj()
                .set("withdrawId", withdraw.getId())
                .set("batchId", withdraw.getBatchId())
                .toString());
        event.setProofImages(StrUtil.blankToDefault(proofImages, null));
        event.setOperatorType(2);
        event.setOperatorId(adminId);
        storeEventMapper.insert(event);
    }

    private void logOperation(Long adminId, String action, Long targetId, String detail) {
        OperationLog log = new OperationLog();
        log.setOperator("admin#" + adminId);
        log.setModule("WITHDRAW");
        log.setAction(action);
        log.setTarget(String.valueOf(targetId));
        log.setDetail(detail);
        log.setIp("");
        operationLogMapper.insert(log);
    }

    private Map<Long, Store> storeMap(Collection<Long> storeIds) {
        List<Long> ids = storeIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return storeMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Store::getId, store -> store));
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

    private WithdrawBatchVO toBatchVO(WithdrawBatch batch) {
        return new WithdrawBatchVO(
                batch.getId(),
                batch.getBatchNo(),
                batch.getTotalAmount(),
                batch.getCount(),
                batch.getFailCount(),
                batch.getFailAmount(),
                batch.getStatus(),
                batch.getExportedAt(),
                batch.getPaidAt(),
                parseImages(batch.getProofImages()),
                batch.getCreatedAt());
    }

    private String yuan(long amount) {
        return BigDecimal.valueOf(amount, 2).toPlainString() + " 元";
    }

    private String csvCell(Object value) {
        if (value instanceof LocalDateTime time) {
            return "\"" + time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\"";
        }
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
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

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
import com.qidirect.mall.domain.dto.admin.AdminParentChangeRequest;
import com.qidirect.mall.domain.dto.admin.AdminResetPhoneRequest;
import com.qidirect.mall.domain.dto.admin.AdminResetPwdRequest;
import com.qidirect.mall.domain.dto.admin.AdminStoreCreateRequest;
import com.qidirect.mall.domain.dto.admin.AdminStoreStatusRequest;
import com.qidirect.mall.domain.dto.admin.BankOverrideRequest;
import com.qidirect.mall.domain.dto.admin.ParentChangeAuditRequest;
import com.qidirect.mall.domain.entity.OperationLog;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.domain.entity.StoreEvent;
import com.qidirect.mall.domain.entity.StoreLevel;
import com.qidirect.mall.domain.entity.StoreParentChange;
import com.qidirect.mall.domain.entity.Wallet;
import com.qidirect.mall.domain.vo.AdminStoreDetailVO;
import com.qidirect.mall.domain.vo.AdminStoreNodeVO;
import com.qidirect.mall.domain.vo.AdminStoreVO;
import com.qidirect.mall.domain.vo.InviteQrVO;
import com.qidirect.mall.domain.vo.StoreProfileVO;
import com.qidirect.mall.domain.vo.WalletVO;
import com.qidirect.mall.infra.mapper.OperationLogMapper;
import com.qidirect.mall.infra.mapper.StoreEventMapper;
import com.qidirect.mall.infra.mapper.StoreLevelMapper;
import com.qidirect.mall.infra.mapper.StoreMapper;
import com.qidirect.mall.infra.mapper.StoreParentChangeMapper;
import com.qidirect.mall.infra.mapper.WalletMapper;
import com.qidirect.mall.infra.security.SecurityUtils;
import com.qidirect.mall.infra.util.DataCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 后台门店操作：收款卡特批代改、任意门店邀请二维码、改上级审批。
 */
@Service
@RequiredArgsConstructor
public class AdminStoreService {

    private static final long MAX_PAGE_SIZE = 100L;
    private static final String INVITE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_LENGTH = 8;
    private static final int STATUS_NORMAL = 1;
    private static final int STATUS_FROZEN = 2;
    /** 后台新建/重置门店统一使用的初始密码，门店首次登录后强制修改 */
    private static final String DEFAULT_PASSWORD = "admin";

    private final StoreMapper storeMapper;
    private final StoreParentChangeMapper storeParentChangeMapper;
    private final StoreEventMapper storeEventMapper;
    private final OperationLogMapper operationLogMapper;
    private final StoreLevelMapper storeLevelMapper;
    private final WalletMapper walletMapper;
    private final StoreGuard storeGuard;
    private final InviteQrService inviteQrService;
    private final StoreProfileService storeProfileService;
    private final WalletService walletService;
    private final DataCipher dataCipher;
    private final StorePathCache storePathCache;
    private final PasswordEncoder passwordEncoder;

    /**
     * 门店列表，支持关键字、档位、状态、实名状态、重复人员与上级筛选。
     */
    public PageResult<AdminStoreVO> page(String keyword, Long levelId, Integer status, Integer authStatus,
                                         Integer duplicateFlag, Long parentId, long page, long size) {
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String kw = StrUtil.trimToEmpty(keyword);
        LambdaQueryWrapper<Store> query = Wrappers.<Store>lambdaQuery()
                .and(StrUtil.isNotBlank(kw), wrapper -> wrapper.like(Store::getName, kw)
                        .or().like(Store::getNickname, kw)
                        .or().like(Store::getPhone, kw))
                .eq(levelId != null, Store::getLevelId, levelId)
                .eq(status != null, Store::getStatus, status)
                .eq(authStatus != null, Store::getAuthStatus, authStatus)
                .eq(duplicateFlag != null, Store::getDuplicateFlag, duplicateFlag)
                .eq(parentId != null, Store::getParentId, parentId)
                .orderByDesc(Store::getId);
        IPage<Store> result = storeMapper.selectPage(new Page<>(current, pageSize), query);
        List<Store> stores = result.getRecords();
        Map<Long, String> parentNames = names(collect(stores.stream().map(Store::getParentId).toList()));
        Map<Long, String> levelNames = levelNames(collect(stores.stream().map(Store::getLevelId).toList()));
        List<AdminStoreVO> records = stores.stream()
                .map(store -> new AdminStoreVO(store.getId(), store.getName(), store.getNickname(),
                        store.getPhone(), store.getParentId(), parentNames.getOrDefault(store.getParentId(), ""),
                        store.getPath(), store.getLevelId(), levelNames.getOrDefault(store.getLevelId(), "未加盟"),
                        store.getLevelPaidAmount(), store.getStatus(), store.getAuthStatus(),
                        store.getDuplicateFlag(), store.getInviteCode(), store.getJoinTime(),
                        store.getCreatedAt()))
                .toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 门店树：返回指定父节点的直接下级，childCount 供前端懒加载。
     */
    public List<AdminStoreNodeVO> tree(Long parentId) {
        long pid = parentId == null ? 0L : parentId;
        if (pid > 0) {
            storeGuard.requireStore(pid);
        }
        List<Store> children = storeMapper.selectList(Wrappers.<Store>lambdaQuery()
                .eq(Store::getParentId, pid)
                .orderByAsc(Store::getId));
        if (children.isEmpty()) {
            return List.of();
        }
        List<Long> childIds = children.stream().map(Store::getId).toList();
        Map<Long, Long> counts = storeMapper.selectList(Wrappers.<Store>lambdaQuery()
                        .select(Store::getParentId)
                        .in(Store::getParentId, childIds))
                .stream()
                .filter(item -> item.getParentId() != null)
                .collect(Collectors.groupingBy(Store::getParentId, Collectors.counting()));
        Map<Long, String> levelNames = levelNames(collect(children.stream().map(Store::getLevelId).toList()));
        return children.stream()
                .map(store -> new AdminStoreNodeVO(store.getId(), store.getName(), store.getNickname(),
                        store.getPhone(), store.getParentId(), store.getPath(), store.getLevelId(),
                        levelNames.getOrDefault(store.getLevelId(), "未加盟"), store.getStatus(),
                        counts.getOrDefault(store.getId(), 0L)))
                .toList();
    }

    /**
     * 门店详情：资料脱敏 + 钱包 + 持卡人特批留痕。
     */
    public AdminStoreDetailVO detail(Long storeId) {
        Store store = storeGuard.requireStore(storeId);
        StoreProfileVO profile = storeProfileService.profile(storeId);
        WalletVO wallet = walletService.getWallet(storeId);
        Store duplicate = store.getDuplicateOf() == null || store.getDuplicateOf() == 0
                ? null : storeMapper.selectById(store.getDuplicateOf());
        List<String> proof = StrUtil.isBlank(store.getBankHolderProof())
                ? List.of() : JSONUtil.toList(store.getBankHolderProof(), String.class);
        return new AdminStoreDetailVO(profile, wallet, store.getBankHolderApproveBy(),
                store.getBankHolderApproveAt(), proof, store.getDuplicateOf(),
                duplicate == null ? "" : displayName(duplicate), store.getRemark(), store.getCreatedAt());
    }

    /**
     * 后台新增门店：生成邀请码、路径与钱包，等待门店实名与加盟。
     */
    @Transactional
    public Long create(AdminStoreCreateRequest request) {
        String phone = request.phone().trim();
        if (findByPhone(phone) != null) {
            throw new BizException(ErrorCode.STORE_PHONE_EXISTS);
        }
        Store parent = null;
        if (request.parentId() != null && request.parentId() > 0) {
            parent = storeGuard.requireStore(request.parentId());
        }
        Store store = new Store();
        store.setParentId(parent == null ? 0L : parent.getId());
        store.setPath("");
        store.setLevelId(0L);
        store.setLevelPaidAmount(0L);
        store.setName(StrUtil.trimToEmpty(request.name()));
        store.setPhone(phone);
        store.setUsername(phone);
        store.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        store.setPwdNeedChange(1);
        store.setNickname(StrUtil.isBlank(request.nickname())
                ? "门店" + phone.substring(phone.length() - 4) : request.nickname().trim());
        store.setAvatar("");
        store.setInviteCode(generateInviteCode());
        store.setWxOpenid("");
        store.setStatus(STATUS_NORMAL);
        store.setRealName(StrUtil.trimToEmpty(request.realName()));
        store.setIdCard("");
        store.setIdCardHash("");
        store.setLicenseNo("");
        store.setLicenseNoHash("");
        store.setAuthStatus(0);
        store.setAuthProvider("");
        store.setAuthTradeNo("");
        store.setDuplicateFlag(0);
        store.setDuplicateOf(0L);
        store.setBankHolder("");
        store.setBankName("");
        store.setBankAccount("");
        store.setBankHolderApproved(0);
        store.setBankHolderApproveBy(0L);
        store.setRemark(StrUtil.trimToEmpty(request.remark()));
        store.setVersion(0);
        storeMapper.insert(store);

        Store pathPatch = new Store();
        pathPatch.setId(store.getId());
        pathPatch.setPath(parent == null ? "/" + store.getId() + "/" : parent.getPath() + store.getId() + "/");
        storeMapper.updateById(pathPatch);

        Wallet wallet = new Wallet();
        wallet.setStoreId(store.getId());
        wallet.setDepositBalance(0L);
        wallet.setCommissionBalance(0L);
        wallet.setFrozenAmount(0L);
        wallet.setVersion(0);
        walletMapper.insert(wallet);

        Long adminId = SecurityUtils.currentUserId();
        logOperation(adminId, "STORE_CREATE", store.getId(), JSONUtil.createObj()
                .set("phone", phone)
                .set("realName", store.getRealName())
                .set("nickname", store.getNickname())
                .set("parentId", store.getParentId())
                .toString());
        return store.getId();
    }

    /**
     * 重置门店手机号：仅改变登录手机号，不影响历史业务数据。
     */
    @Transactional
    public void resetPhone(Long storeId, AdminResetPhoneRequest request) {
        Store store = storeGuard.requireStore(storeId);
        String phone = request.phone().trim();
        Store holder = findByPhone(phone);
        if (holder != null && !Objects.equals(holder.getId(), storeId)) {
            throw new BizException(ErrorCode.STORE_PHONE_EXISTS);
        }
        String oldPhone = store.getPhone();
        Store patch = new Store();
        patch.setId(storeId);
        patch.setPhone(phone);
        storeMapper.updateById(patch);
        logOperation(SecurityUtils.currentUserId(), "STORE_RESET_PHONE", storeId, JSONUtil.createObj()
                .set("oldPhone", oldPhone)
                .set("newPhone", phone)
                .toString());
    }

    /**
     * 重置门店登录密码为初始密码 admin，并标记为需强制修改（忘记密码时厂家使用）。
     */
    @Transactional
    public void resetPassword(Long storeId) {
        Store store = storeGuard.requireStore(storeId);
        Store patch = new Store();
        patch.setId(storeId);
        if (StrUtil.isBlank(store.getUsername())) {
            patch.setUsername(store.getPhone());
        }
        patch.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        patch.setPwdNeedChange(1);
        storeMapper.updateById(patch);
        logOperation(SecurityUtils.currentUserId(), "STORE_RESET_PASSWORD", storeId,
                JSONUtil.createObj().set("resetToDefault", true).toString());
    }

    /**
     * 冻结门店：写 FREEZE 事件与操作日志，登录/下单等受限由 StoreGuard 拦截。
     */
    @Transactional
    public void freeze(Long storeId, AdminStoreStatusRequest request) {
        changeStatus(storeId, STATUS_FROZEN, "FREEZE", "门店已冻结", request);
    }

    /**
     * 解冻门店：恢复全部操作并写 UNFREEZE 事件。
     */
    @Transactional
    public void unfreeze(Long storeId, AdminStoreStatusRequest request) {
        changeStatus(storeId, STATUS_NORMAL, "UNFREEZE", "门店已解冻", request);
    }

    private void changeStatus(Long storeId, int target, String eventType, String title,
                              AdminStoreStatusRequest request) {
        Store store = storeGuard.requireStore(storeId);
        if (Objects.equals(store.getStatus(), target)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    target == STATUS_FROZEN ? "门店已是冻结状态" : "门店已是正常状态");
        }
        Store patch = new Store();
        patch.setId(storeId);
        patch.setStatus(target);
        storeMapper.updateById(patch);

        Long adminId = SecurityUtils.currentUserId();
        StoreEvent event = new StoreEvent();
        event.setStoreId(storeId);
        event.setEventType(eventType);
        event.setTitle(title);
        event.setAmount(0L);
        event.setDirection(0);
        event.setAccount(0);
        event.setDetail(JSONUtil.createObj()
                .set("reason", StrUtil.nullToEmpty(request == null ? null : request.reason()))
                .toString());
        event.setOperatorType(2);
        event.setOperatorId(adminId);
        storeEventMapper.insert(event);

        logOperation(adminId, eventType, storeId, JSONUtil.createObj()
                .set("before", store.getStatus())
                .set("after", target)
                .set("reason", StrUtil.nullToEmpty(request == null ? null : request.reason()))
                .toString());
    }

    private Store findByPhone(String phone) {
        return storeMapper.selectOne(Wrappers.<Store>lambdaQuery().eq(Store::getPhone, phone));
    }

    private String generateInviteCode() {
        for (int i = 0; i < 10; i++) {
            String code = RandomUtil.randomString(INVITE_CHARS, INVITE_LENGTH);
            Long count = storeMapper.selectCount(Wrappers.<Store>lambdaQuery()
                    .eq(Store::getInviteCode, code));
            if (count == null || count == 0) {
                return code;
            }
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR);
    }

    private String displayName(Store store) {
        return StrUtil.blankToDefault(store.getName(), StrUtil.nullToEmpty(store.getNickname()));
    }

    private List<Long> collect(Collection<Long> values) {
        return values.stream().filter(id -> id != null && id > 0).distinct().toList();
    }

    private Map<Long, String> names(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return storeMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Store::getId, this::displayName));
    }

    private Map<Long, String> levelNames(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return storeLevelMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(StoreLevel::getId, StoreLevel::getName));
    }

    public void overrideBank(Long storeId, BankOverrideRequest request) {
        Store store = storeGuard.requireStore(storeId);
        Long adminId = SecurityUtils.currentUserId();
        List<String> proofImages = request.proofImages() == null ? List.of() : request.proofImages();

        Store patch = new Store();
        patch.setId(store.getId());
        patch.setBankHolder(request.bankHolder().trim());
        patch.setBankName(request.bankName().trim());
        patch.setBankAccount(dataCipher.encrypt(request.bankAccount().trim()));
        patch.setBankHolderApproved(1);
        patch.setBankHolderApproveBy(adminId);
        patch.setBankHolderApproveAt(LocalDateTime.now());
        patch.setBankHolderProof(JSONUtil.toJsonStr(proofImages));
        storeMapper.updateById(patch);

        logOperation(adminId, "BANK_OVERRIDE", storeId, JSONUtil.createObj()
                .set("bankHolder", patch.getBankHolder())
                .set("bankName", patch.getBankName())
                .set("proofImages", proofImages)
                .set("reason", request.reason())
                .toString());
    }

    public InviteQrVO inviteQr(Long storeId) {
        Store store = storeGuard.requireStore(storeId);
        storeGuard.assertNotFrozen(store);
        return inviteQrService.build(store);
    }

    public List<StoreParentChange> listParentChanges(Integer status) {
        return storeParentChangeMapper.selectList(Wrappers.<StoreParentChange>lambdaQuery()
                .eq(status != null, StoreParentChange::getStatus, status)
                .orderByDesc(StoreParentChange::getId));
    }

    @Transactional
    public void auditParentChange(Long changeId, ParentChangeAuditRequest request) {
        StoreParentChange change = storeParentChangeMapper.selectById(changeId);
        if (change == null || !Objects.equals(change.getStatus(), 0)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "申请不存在或已处理");
        }
        Store store = storeGuard.requireStore(change.getStoreId());
        storeGuard.assertNotFrozen(store);
        Long adminId = SecurityUtils.currentUserId();
        String approver = "admin#" + adminId;

        if (!Boolean.TRUE.equals(request.approved())) {
            change.setStatus(2);
            change.setApprover(approver);
            storeParentChangeMapper.updateById(change);
            logOperation(adminId, "PARENT_CHANGE_REJECT", store.getId(), JSONUtil.createObj()
                    .set("changeId", changeId)
                    .set("remark", StrUtil.nullToEmpty(request.remark()))
                    .toString());
            return;
        }

        Store newParent = resolveNewParent(store, change.getNewParentId());
        if (hasRealReceipt(store) && !SecurityUtils.hasAuthority("*")) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "已产生实收的门店仅超管可审批改上级");
        }

        String oldPath = StrUtil.nullToEmpty(store.getPath());
        String newPath = buildPath(store, newParent);
        applyParentChange(store, change.getNewParentId(), oldPath, newPath);

        change.setStatus(1);
        change.setNewPath(newPath);
        change.setApprover(approver);
        storeParentChangeMapper.updateById(change);

        insertParentChangeEvent(store, change, oldPath, newPath, adminId);

        logOperation(adminId, "PARENT_CHANGE_APPROVE", store.getId(), JSONUtil.createObj()
                .set("changeId", changeId)
                .set("oldPath", oldPath)
                .set("newPath", newPath)
                .set("remark", StrUtil.nullToEmpty(request.remark()))
                .toString());
    }

    /**
     * 实收前厂家直接调整上级：写入已通过的改上级记录并同步子树路径。
     */
    @Transactional
    public void changeParentDirect(Long storeId, AdminParentChangeRequest request) {
        Store store = storeGuard.requireStore(storeId);
        storeGuard.assertNotFrozen(store);
        Long adminId = SecurityUtils.currentUserId();
        if (hasRealReceipt(store)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "门店已产生实收，请提交改上级申请并由超管审批");
        }
        Long newParentId = request.newParentId();
        if (Objects.equals(newParentId, store.getParentId())) {
            throw new BizException(ErrorCode.PARENT_CHANGE_INVALID.getCode(), "目标上级与当前上级相同");
        }
        Long pending = storeParentChangeMapper.selectCount(Wrappers.<StoreParentChange>lambdaQuery()
                .eq(StoreParentChange::getStoreId, storeId)
                .eq(StoreParentChange::getStatus, 0));
        if (pending != null && pending > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "存在待审批的改上级申请，请先处理");
        }
        Store newParent = resolveNewParent(store, newParentId);
        String oldPath = StrUtil.nullToEmpty(store.getPath());
        String newPath = buildPath(store, newParent);

        StoreParentChange change = new StoreParentChange();
        change.setStoreId(store.getId());
        change.setOldParentId(store.getParentId());
        change.setNewParentId(newParentId);
        change.setOldPath(oldPath);
        change.setNewPath(newPath);
        change.setReason(StrUtil.nullToEmpty(request.reason()));
        change.setStatus(1);
        change.setApplicant("admin#" + adminId);
        change.setApprover("admin#" + adminId);
        storeParentChangeMapper.insert(change);

        applyParentChange(store, newParentId, oldPath, newPath);
        insertParentChangeEvent(store, change, oldPath, newPath, adminId);
        logOperation(adminId, "PARENT_CHANGE_DIRECT", store.getId(), JSONUtil.createObj()
                .set("changeId", change.getId())
                .set("oldParentId", change.getOldParentId())
                .set("newParentId", newParentId)
                .set("oldPath", oldPath)
                .set("newPath", newPath)
                .set("reason", StrUtil.nullToEmpty(request.reason()))
                .toString());
    }

    private Store resolveNewParent(Store store, Long newParentId) {
        if (newParentId == null || newParentId == 0) {
            return null;
        }
        Store newParent = storeMapper.selectById(newParentId);
        if (newParent == null || StrUtil.isBlank(newParent.getPath())) {
            throw new BizException(ErrorCode.PARENT_CHANGE_INVALID);
        }
        String storePath = StrUtil.nullToEmpty(store.getPath());
        if (StrUtil.isNotBlank(storePath) && newParent.getPath().startsWith(storePath)) {
            throw new BizException(ErrorCode.PARENT_CHANGE_INVALID.getCode(), "不能将上级调整为自身或下级");
        }
        return newParent;
    }

    private String buildPath(Store store, Store newParent) {
        return newParent == null
                ? "/" + store.getId() + "/"
                : newParent.getPath() + store.getId() + "/";
    }

    /**
     * 更新自身路径、批量重写子树路径，并清理受影响门店的 path 缓存。
     */
    private void applyParentChange(Store store, Long newParentId, String oldPath, String newPath) {
        List<Long> affected = StrUtil.isBlank(oldPath)
                ? List.of(store.getId())
                : storeMapper.selectList(Wrappers.<Store>lambdaQuery()
                        .select(Store::getId)
                        .likeRight(Store::getPath, oldPath))
                .stream().map(Store::getId).toList();

        storeMapper.update(null, Wrappers.<Store>lambdaUpdate()
                .eq(Store::getId, store.getId())
                .set(Store::getParentId, newParentId)
                .set(Store::getPath, newPath)
                .setSql("version = version + 1"));
        if (StrUtil.isNotBlank(oldPath) && !oldPath.equals(newPath)) {
            storeMapper.updateDescendantPaths(oldPath, newPath, oldPath.length() + 1, store.getId());
        }
        storePathCache.evictAll(affected);
    }

    private void insertParentChangeEvent(Store store, StoreParentChange change,
                                         String oldPath, String newPath, Long adminId) {
        StoreEvent event = new StoreEvent();
        event.setStoreId(store.getId());
        event.setEventType("PARENT_CHANGE");
        event.setBizNo("PC" + change.getId());
        event.setTitle("调整上级：" + change.getOldParentId() + " → " + change.getNewParentId());
        event.setAmount(0L);
        event.setDirection(0);
        event.setAccount(0);
        event.setDetail(JSONUtil.createObj()
                .set("changeId", change.getId())
                .set("oldParentId", change.getOldParentId())
                .set("newParentId", change.getNewParentId())
                .set("oldPath", oldPath)
                .set("newPath", newPath)
                .toString());
        event.setOperatorType(2);
        event.setOperatorId(adminId);
        storeEventMapper.insert(event);
    }

    /**
     * 是否已产生实收：加盟/升级后 join_time 或 level_paid_amount 会有值。
     */
    private boolean hasRealReceipt(Store store) {
        return store.getJoinTime() != null
                || (store.getLevelPaidAmount() != null && store.getLevelPaidAmount() > 0);
    }

    private void logOperation(Long adminId, String action, Long storeId, String detail) {
        OperationLog log = new OperationLog();
        log.setOperator("admin#" + adminId);
        log.setModule("STORE");
        log.setAction(action);
        log.setTarget(String.valueOf(storeId));
        log.setDetail(detail);
        log.setIp("");
        operationLogMapper.insert(log);
    }
}

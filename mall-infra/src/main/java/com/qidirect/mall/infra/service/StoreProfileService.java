package com.qidirect.mall.infra.service;

import cn.hutool.core.util.IdcardUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.dto.store.ParentChangeRequest;
import com.qidirect.mall.domain.dto.store.StoreAuthRequest;
import com.qidirect.mall.domain.dto.store.StoreBankRequest;
import com.qidirect.mall.domain.dto.store.StoreProfileUpdateRequest;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.domain.entity.StoreLevel;
import com.qidirect.mall.domain.entity.StoreParentChange;
import com.qidirect.mall.domain.vo.InviteQrVO;
import com.qidirect.mall.domain.vo.OrderEligibilityVO;
import com.qidirect.mall.domain.vo.StoreProfileVO;
import com.qidirect.mall.infra.mapper.StoreLevelMapper;
import com.qidirect.mall.infra.mapper.StoreMapper;
import com.qidirect.mall.infra.mapper.StoreParentChangeMapper;
import com.qidirect.mall.infra.util.DataCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 门店资料、实名、收款卡、邀请二维码与改上级申请。
 */
@Service
@RequiredArgsConstructor
public class StoreProfileService {

    private final StoreMapper storeMapper;
    private final StoreLevelMapper storeLevelMapper;
    private final StoreParentChangeMapper storeParentChangeMapper;
    private final StoreGuard storeGuard;
    private final InviteQrService inviteQrService;
    private final DataCipher dataCipher;

    public StoreProfileVO profile(Long storeId) {
        Store store = storeGuard.requireStore(storeId);
        StoreLevel level = store.getLevelId() != null && store.getLevelId() > 0
                ? storeLevelMapper.selectById(store.getLevelId())
                : null;
        Store parent = store.getParentId() != null && store.getParentId() > 0
                ? storeMapper.selectById(store.getParentId())
                : null;
        return new StoreProfileVO(
                store.getId(),
                store.getPhone(),
                store.getNickname(),
                store.getAvatar(),
                store.getName(),
                store.getParentId(),
                parent == null ? "" : StrUtil.blankToDefault(parent.getName(), parent.getNickname()),
                store.getLevelId(),
                level == null ? "未加盟" : level.getName(),
                store.getLevelPaidAmount(),
                store.getJoinTime(),
                store.getUpgradeDeadline(),
                store.getInviteCode(),
                store.getStatus(),
                store.getAuthStatus(),
                store.getRealName(),
                maskIdCard(decryptQuietly(store.getIdCard())),
                store.getLicenseNo(),
                parseImages(store.getBizImages()),
                store.getBankHolder(),
                store.getBankName(),
                maskBankAccount(decryptQuietly(store.getBankAccount())),
                store.getDuplicateFlag());
    }

    public StoreProfileVO updateProfile(Long storeId, StoreProfileUpdateRequest request) {
        Store store = storeGuard.requireStore(storeId);
        storeGuard.assertNotFrozen(store);
        Store patch = new Store();
        patch.setId(storeId);
        if (request.nickname() != null) {
            patch.setNickname(request.nickname().trim());
        }
        if (request.avatar() != null) {
            patch.setAvatar(request.avatar().trim());
        }
        storeMapper.updateById(patch);
        return profile(storeId);
    }

    @Transactional
    public StoreProfileVO submitAuth(Long storeId, StoreAuthRequest request) {
        Store store = storeGuard.requireStore(storeId);
        storeGuard.assertNotFrozen(store);

        String idCard = request.idCard().trim().toUpperCase();
        if (!IdcardUtil.isValidCard(idCard)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "身份证号不正确");
        }
        String licenseNo = StrUtil.trimToEmpty(request.licenseNo()).toUpperCase();
        String idCardHash = dataCipher.hash(idCard);
        String licenseHash = dataCipher.hash(licenseNo);

        Store duplicate = findDuplicate(storeId, idCardHash, licenseHash);
        List<String> bizImages = request.bizImages() == null ? List.of() : request.bizImages();

        Store patch = new Store();
        patch.setId(storeId);
        // 门店名称只在前端填写时才覆盖，实名只要姓名与身份证
        if (StrUtil.isNotBlank(request.storeName())) {
            patch.setName(request.storeName().trim());
        }
        patch.setRealName(request.realName().trim());
        patch.setIdCard(dataCipher.encrypt(idCard));
        patch.setIdCardHash(idCardHash);
        patch.setLicenseNo(licenseNo);
        patch.setLicenseNoHash(licenseHash);
        patch.setBizImages(JSONUtil.toJsonStr(bizImages));
        patch.setAuthStatus(1);
        patch.setDuplicateFlag(duplicate == null ? 0 : 1);
        patch.setDuplicateOf(duplicate == null ? 0L : duplicate.getId());
        storeMapper.updateById(patch);
        return profile(storeId);
    }

    public StoreProfileVO bindBank(Long storeId, StoreBankRequest request) {
        Store store = storeGuard.requireStore(storeId);
        storeGuard.assertNotFrozen(store);
        if (store.getAuthStatus() == null || store.getAuthStatus() == 0) {
            throw new BizException(ErrorCode.STORE_NOT_VERIFIED);
        }

        String holder = request.bankHolder().trim();
        boolean sameAsRealName = holder.equals(StrUtil.trimToEmpty(store.getRealName()));
        boolean approvedSame = Objects.equals(store.getBankHolderApproved(), 1)
                && holder.equals(StrUtil.trimToEmpty(store.getBankHolder()));
        if (!sameAsRealName && !approvedSame) {
            throw new BizException(ErrorCode.BANK_HOLDER_MISMATCH);
        }

        var update = Wrappers.<Store>lambdaUpdate()
                .eq(Store::getId, storeId)
                .set(Store::getBankHolder, holder)
                .set(Store::getBankName, request.bankName().trim())
                .set(Store::getBankAccount, dataCipher.encrypt(request.bankAccount().trim()))
                .set(Store::getBankHolderApproved, sameAsRealName ? 0 : 1);
        if (sameAsRealName) {
            update.set(Store::getBankHolderApproveBy, 0L)
                    .set(Store::getBankHolderApproveAt, null)
                    .set(Store::getBankHolderProof, null);
        }
        storeMapper.update(null, update);
        return profile(storeId);
    }

    public InviteQrVO inviteQr(Long storeId) {
        Store store = storeGuard.requireStore(storeId);
        storeGuard.assertNotFrozen(store);
        if (store.getLevelId() == null || store.getLevelId() == 0) {
            throw new BizException(ErrorCode.STORE_NOT_JOINED);
        }
        return inviteQrService.build(store);
    }

    public void submitParentChange(Long storeId, ParentChangeRequest request) {
        Store store = storeGuard.requireStore(storeId);
        storeGuard.assertNotFrozen(store);

        Long newParentId = request.newParentId();
        if (Objects.equals(newParentId, store.getParentId())) {
            throw new BizException(ErrorCode.PARENT_CHANGE_INVALID.getCode(), "目标上级与当前上级相同");
        }
        if (newParentId != 0) {
            Store newParent = storeMapper.selectById(newParentId);
            if (newParent == null || StrUtil.isBlank(newParent.getPath())) {
                throw new BizException(ErrorCode.PARENT_CHANGE_INVALID);
            }
            assertNotDescendant(store, newParent);
        }

        Long pending = storeParentChangeMapper.selectCount(Wrappers.<StoreParentChange>lambdaQuery()
                .eq(StoreParentChange::getStoreId, storeId)
                .eq(StoreParentChange::getStatus, 0));
        if (pending != null && pending > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "已有待审批的改上级申请");
        }

        StoreParentChange change = new StoreParentChange();
        change.setStoreId(storeId);
        change.setOldParentId(store.getParentId());
        change.setNewParentId(newParentId);
        change.setOldPath(StrUtil.nullToEmpty(store.getPath()));
        change.setNewPath("");
        change.setReason(StrUtil.nullToEmpty(request.reason()));
        change.setStatus(0);
        change.setApplicant(store.getPhone());
        change.setApprover("");
        storeParentChangeMapper.insert(change);
    }

    public OrderEligibilityVO orderEligibility(Long storeId) {
        return storeGuard.eligibility(storeId);
    }

    private Store findDuplicate(Long storeId, String idCardHash, String licenseHash) {
        LambdaQueryWrapper<Store> query = Wrappers.<Store>lambdaQuery()
                .ne(Store::getId, storeId)
                .orderByAsc(Store::getId)
                .last("LIMIT 1");
        if (StrUtil.isBlank(licenseHash)) {
            query.eq(Store::getIdCardHash, idCardHash);
        } else {
            query.and(wrapper -> wrapper.eq(Store::getIdCardHash, idCardHash)
                    .or().eq(Store::getLicenseNoHash, licenseHash));
        }
        return storeMapper.selectOne(query);
    }

    private void assertNotDescendant(Store store, Store newParent) {
        String path = StrUtil.nullToEmpty(store.getPath());
        if (StrUtil.isNotBlank(path) && newParent.getPath().startsWith(path)) {
            throw new BizException(ErrorCode.PARENT_CHANGE_INVALID.getCode(), "不能将上级调整为自身或下级");
        }
    }

    private String decryptQuietly(String cipherText) {
        try {
            return dataCipher.decrypt(cipherText);
        } catch (Exception e) {
            return "";
        }
    }

    private String maskIdCard(String idCard) {
        if (StrUtil.isBlank(idCard)) {
            return "";
        }
        if (idCard.length() <= 8) {
            return "****";
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
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

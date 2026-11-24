package com.qidirect.mall.infra.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.common.constant.AuthConstants;
import com.qidirect.mall.common.enums.UserType;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.common.util.JwtUtil;
import com.qidirect.mall.domain.dto.auth.ChangePasswordRequest;
import com.qidirect.mall.domain.dto.auth.PwdLoginRequest;
import com.qidirect.mall.domain.dto.auth.RegisterRequest;
import com.qidirect.mall.domain.dto.auth.SmsLoginRequest;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.domain.entity.Wallet;
import com.qidirect.mall.domain.vo.StoreLoginVO;
import com.qidirect.mall.infra.mapper.StoreMapper;
import com.qidirect.mall.infra.mapper.WalletMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 门店注册与登录。注册时按邀请码绑定上级并生成物化路径，同时初始化钱包。
 */
@Service
@RequiredArgsConstructor
public class StoreAuthService {

    private static final String INVITE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_LENGTH = 8;

    private final StoreMapper storeMapper;
    private final WalletMapper walletMapper;
    private final SmsCodeService smsCodeService;
    private final JwtUtil jwtUtil;
    private final StorePathCache storePathCache;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public StoreLoginVO register(RegisterRequest request) {
        String phone = request.phone().trim();
        if (findByPhone(phone) != null) {
            throw new BizException(ErrorCode.STORE_PHONE_EXISTS);
        }
        Store parent = resolveParent(request.inviteCode());
        smsCodeService.verify(phone, request.code());

        Store store = new Store();
        store.setParentId(parent == null ? 0L : parent.getId());
        store.setPath("");
        store.setLevelId(0L);
        store.setLevelPaidAmount(0L);
        store.setName("");
        store.setPhone(phone);
        store.setUsername(phone);
        store.setPassword(null);
        store.setNickname(StrUtil.isBlank(request.nickname())
                ? "门店" + phone.substring(phone.length() - 4)
                : request.nickname().trim());
        store.setAvatar("");
        store.setInviteCode(generateInviteCode());
        store.setWxOpenid("");
        store.setStatus(1);
        store.setRealName("");
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
        store.setRemark("");
        store.setVersion(0);
        storeMapper.insert(store);

        String path = parent == null
                ? "/" + store.getId() + "/"
                : parent.getPath() + store.getId() + "/";
        Store pathPatch = new Store();
        pathPatch.setId(store.getId());
        pathPatch.setPath(path);
        storeMapper.updateById(pathPatch);
        store.setPath(path);

        Wallet wallet = new Wallet();
        wallet.setStoreId(store.getId());
        wallet.setDepositBalance(0L);
        wallet.setCommissionBalance(0L);
        wallet.setFrozenAmount(0L);
        wallet.setVersion(0);
        walletMapper.insert(wallet);

        return toLoginVO(store);
    }

    public StoreLoginVO login(SmsLoginRequest request) {
        String phone = request.phone().trim();
        Store store = findByPhone(phone);
        if (store == null) {
            throw new BizException(ErrorCode.STORE_PHONE_NOT_FOUND);
        }
        if (store.getStatus() != null && store.getStatus() == 2) {
            throw new BizException(ErrorCode.STORE_FROZEN);
        }
        smsCodeService.verify(phone, request.code());
        return toLoginVO(store);
    }

    /**
     * 用户名 + 密码登录。username 默认为手机号；password 为 BCrypt 密文，
     * 未设置密码（NULL）时不可登录，需由后台「设置登录密码」开启。
     */
    public StoreLoginVO loginByUsername(PwdLoginRequest request) {
        String username = request.username().trim();
        Store store = storeMapper.selectOne(
                Wrappers.<Store>lambdaQuery().eq(Store::getUsername, username));
        if (store == null || store.getPassword() == null
                || !passwordEncoder.matches(request.password(), store.getPassword())) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        if (store.getStatus() != null && store.getStatus() == 2) {
            throw new BizException(ErrorCode.STORE_FROZEN);
        }
        return toLoginVO(store);
    }

    /**
     * 门店自助修改密码：首次用初始密码登录后必须调用，改完清除强制修改标记。
     */
    @Transactional
    public void changePassword(Long storeId, ChangePasswordRequest request) {
        Store store = storeMapper.selectById(storeId);
        if (store == null) {
            throw new BizException(ErrorCode.STORE_NOT_FOUND);
        }
        if (store.getPassword() == null
                || !passwordEncoder.matches(request.oldPassword(), store.getPassword())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "原密码不正确");
        }
        String newPwd = request.newPassword().trim();
        if ("admin".equalsIgnoreCase(newPwd)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "新密码不能与初始密码相同");
        }
        Store patch = new Store();
        patch.setId(storeId);
        patch.setPassword(passwordEncoder.encode(newPwd));
        patch.setPwdNeedChange(0);
        storeMapper.updateById(patch);
    }

    private Store resolveParent(String inviteCode) {
        if (StrUtil.isBlank(inviteCode)) {
            return null;
        }
        String code = inviteCode.trim().toUpperCase();
        Store parent = storeMapper.selectOne(Wrappers.<Store>lambdaQuery()
                .eq(Store::getInviteCode, code));
        if (parent == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "邀请码无效");
        }
        return parent;
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

    private StoreLoginVO toLoginVO(Store store) {
        storePathCache.put(store.getId(), store.getPath());
        Map<String, Object> claims = new HashMap<>();
        claims.put(AuthConstants.CLAIM_STORE_ID, store.getId());
        claims.put(AuthConstants.CLAIM_LEVEL_ID, store.getLevelId());
        String token = jwtUtil.createToken(UserType.STORE, store.getId(), claims);
        return new StoreLoginVO(token, store.getId(), store.getPhone(), store.getNickname(),
                store.getLevelId(), store.getAuthStatus(), store.getInviteCode(),
                store.getPwdNeedChange() != null && store.getPwdNeedChange() == 1);
    }
}

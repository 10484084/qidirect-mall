package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.common.constant.AuthConstants;
import com.qidirect.mall.common.enums.UserType;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.common.util.JwtUtil;
import com.qidirect.mall.domain.dto.admin.AdminLoginRequest;
import com.qidirect.mall.domain.entity.AdminUser;
import com.qidirect.mall.domain.vo.AdminLoginVO;
import com.qidirect.mall.infra.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 后台管理员登录。perms 由角色 JSON 配置展开后写入 JWT。
 */
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SmsCodeService smsCodeService;

    public AdminLoginVO login(AdminLoginRequest request) {
        AdminUser admin = adminUserMapper.selectOne(Wrappers.<AdminUser>lambdaQuery()
                .eq(AdminUser::getAccount, request.account().trim()));
        if (admin == null || admin.getStatus() == null || admin.getStatus() != 1
                || !passwordEncoder.matches(request.password(), admin.getPassword())) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        List<String> perms = expandPerms(adminUserMapper.selectPerms(admin.getId()));
        String token = jwtUtil.createToken(UserType.ADMIN, admin.getId(),
                Map.of(AuthConstants.CLAIM_PERMS, perms));
        return new AdminLoginVO(token, admin.getId(), admin.getAccount(),
                admin.getRealName(), StrUtil.nullToEmpty(admin.getPhone()), perms);
    }

    /**
     * 忘记密码：仅当手机号确实绑定在启用的管理员上才发送验证码。
     * 前端提示统一为「若该手机号已绑定，验证码已发送」，避免手机号枚举。
     */
    public void sendResetCode(String phone) {
        String trimmed = StrUtil.trimToEmpty(phone);
        AdminUser admin = adminUserMapper.selectOne(Wrappers.<AdminUser>lambdaQuery()
                .eq(AdminUser::getPhone, trimmed)
                .eq(AdminUser::getStatus, 1)
                .last("limit 1"));
        if (admin == null) {
            return;
        }
        smsCodeService.send(trimmed);
    }

    /**
     * 校验手机号与验证码后重置密码；同一手机号命中多个账号时更新全部（实际业务中手机号唯一）。
     */
    public void resetPassword(String phone, String code, String newPassword) {
        String trimmed = StrUtil.trimToEmpty(phone);
        AdminUser admin = adminUserMapper.selectOne(Wrappers.<AdminUser>lambdaQuery()
                .eq(AdminUser::getPhone, trimmed)
                .eq(AdminUser::getStatus, 1)
                .last("limit 1"));
        if (admin == null) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID);
        }
        smsCodeService.verify(trimmed, StrUtil.trimToEmpty(code));
        AdminUser update = new AdminUser();
        update.setId(admin.getId());
        update.setPassword(passwordEncoder.encode(newPassword));
        adminUserMapper.updateById(update);
    }

    /**
     * 自助修改手机号：先向「当前已绑定手机号」发验证码，校验通过才允许换绑。
     */
    public void sendChangePhoneCode(Long adminId) {
        AdminUser admin = adminUserMapper.selectById(adminId);
        if (admin == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "账号不存在");
        }
        if (StrUtil.isBlank(admin.getPhone())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "当前账号未绑定手机号，请联系超级管理员处理");
        }
        smsCodeService.send(admin.getPhone());
    }

    public void changePhone(Long adminId, String code, String newPhone) {
        AdminUser admin = adminUserMapper.selectById(adminId);
        if (admin == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "账号不存在");
        }
        String oldPhone = StrUtil.trimToEmpty(admin.getPhone());
        if (StrUtil.isBlank(oldPhone)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "当前账号未绑定手机号，请联系超级管理员处理");
        }
        String trimmedNew = StrUtil.trimToEmpty(newPhone);
        if (trimmedNew.equals(oldPhone)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "新手机号与当前手机号相同");
        }
        // 验证码发到原手机号
        smsCodeService.verify(oldPhone, StrUtil.trimToEmpty(code));
        Long used = adminUserMapper.selectCount(Wrappers.<AdminUser>lambdaQuery()
                .eq(AdminUser::getPhone, trimmedNew));
        if (used != null && used > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "该手机号已被其他管理员绑定");
        }
        AdminUser update = new AdminUser();
        update.setId(adminId);
        update.setPhone(trimmedNew);
        adminUserMapper.updateById(update);
    }

    private List<String> expandPerms(List<String> rawPerms) {
        LinkedHashSet<String> perms = new LinkedHashSet<>();
        if (rawPerms != null) {
            for (String raw : rawPerms) {
                if (StrUtil.isNotBlank(raw)) {
                    perms.addAll(JSONUtil.toList(raw, String.class));
                }
            }
        }
        return new ArrayList<>(perms);
    }
}

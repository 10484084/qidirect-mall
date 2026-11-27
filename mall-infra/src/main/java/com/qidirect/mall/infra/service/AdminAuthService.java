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
                admin.getRealName(), perms);
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

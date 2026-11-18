package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.dto.admin.AdminUserSaveRequest;
import com.qidirect.mall.domain.dto.admin.AdminUserUpdateRequest;
import com.qidirect.mall.domain.entity.AdminRole;
import com.qidirect.mall.domain.entity.AdminUser;
import com.qidirect.mall.domain.entity.AdminUserRole;
import com.qidirect.mall.domain.entity.OperationLog;
import com.qidirect.mall.domain.vo.AdminUserVO;
import com.qidirect.mall.infra.mapper.AdminRoleMapper;
import com.qidirect.mall.infra.mapper.AdminUserMapper;
import com.qidirect.mall.infra.mapper.AdminUserRoleMapper;
import com.qidirect.mall.infra.mapper.OperationLogMapper;
import com.qidirect.mall.infra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 后台管理员管理。account 唯一，密码 BCrypt；角色关联按需整体重建。
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final long MAX_PAGE_SIZE = 100L;

    private final AdminUserMapper adminUserMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final OperationLogMapper operationLogMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResult<AdminUserVO> page(String keyword, Integer status, long page, long size) {
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String kw = StrUtil.trimToNull(keyword);
        LambdaQueryWrapper<AdminUser> query = Wrappers.<AdminUser>lambdaQuery()
                .and(kw != null, w -> w.like(AdminUser::getAccount, kw)
                        .or().like(AdminUser::getRealName, kw))
                .eq(status != null, AdminUser::getStatus, status)
                .orderByDesc(AdminUser::getId);
        IPage<AdminUser> result = adminUserMapper.selectPage(new Page<>(current, pageSize), query);
        return PageResult.of(toVOs(result.getRecords()), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Transactional
    public void create(AdminUserSaveRequest request) {
        Long adminId = SecurityUtils.currentUserId();
        validateStatus(request.status());
        Long existing = adminUserMapper.selectCount(Wrappers.<AdminUser>lambdaQuery()
                .eq(AdminUser::getAccount, request.account().trim()));
        if (existing != null && existing > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "账号已存在");
        }
        AdminUser admin = new AdminUser();
        admin.setAccount(request.account().trim());
        admin.setPassword(passwordEncoder.encode(request.password()));
        admin.setRealName(StrUtil.nullToEmpty(request.realName()));
        admin.setStatus(request.status() == null ? 1 : request.status());
        try {
            adminUserMapper.insert(admin);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "账号已存在");
        }
        replaceRoles(admin.getId(), request.roleIds());
        logOperation(adminId, "ADMIN_CREATE", admin.getId(),
                JSONUtil.createObj()
                        .set("account", admin.getAccount())
                        .set("status", admin.getStatus())
                        .set("roleIds", request.roleIds())
                        .toString());
    }

    @Transactional
    public void update(Long id, AdminUserUpdateRequest request) {
        AdminUser admin = adminUserMapper.selectById(id);
        if (admin == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "管理员不存在");
        }
        Long adminId = SecurityUtils.currentUserId();
        validateStatus(request.status());
        if (StrUtil.isNotBlank(request.password())) {
            admin.setPassword(passwordEncoder.encode(request.password()));
        }
        if (request.realName() != null) {
            admin.setRealName(request.realName().trim());
        }
        if (request.status() != null) {
            admin.setStatus(request.status());
        }
        adminUserMapper.updateById(admin);
        if (request.roleIds() != null) {
            replaceRoles(id, request.roleIds());
        }
        logOperation(adminId, "ADMIN_UPDATE", id,
                JSONUtil.createObj()
                        .set("account", admin.getAccount())
                        .set("status", admin.getStatus())
                        .set("roleIds", request.roleIds())
                        .set("passwordChanged", StrUtil.isNotBlank(request.password()))
                        .toString());
    }

    private void replaceRoles(Long adminId, List<Long> roleIds) {
        if (roleIds == null) {
            return;
        }
        adminUserRoleMapper.deleteByAdminId(adminId);
        for (Long roleId : new LinkedHashSet<>(roleIds)) {
            if (roleId != null) {
                adminUserRoleMapper.insertOne(adminId, roleId);
            }
        }
    }

    private void validateStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "管理员状态不正确");
        }
    }

    private List<AdminUserVO> toVOs(List<AdminUser> admins) {
        if (admins.isEmpty()) {
            return List.of();
        }
        List<Long> adminIds = admins.stream().map(AdminUser::getId).toList();
        List<AdminUserRole> links = adminUserRoleMapper.selectList(Wrappers.<AdminUserRole>lambdaQuery()
                .in(AdminUserRole::getAdminId, adminIds)
                .orderByAsc(AdminUserRole::getRoleId));
        Map<Long, List<Long>> roleIdsByAdmin = links.stream().collect(Collectors.groupingBy(
                AdminUserRole::getAdminId, LinkedHashMap::new,
                Collectors.mapping(AdminUserRole::getRoleId, Collectors.toList())));
        List<Long> roleIds = links.stream().map(AdminUserRole::getRoleId).distinct().toList();
        Map<Long, String> roleNames = roleIds.isEmpty()
                ? Map.of()
                : adminRoleMapper.selectBatchIds(roleIds).stream()
                        .collect(Collectors.toMap(AdminRole::getId, AdminRole::getName, (a, b) -> a));
        return admins.stream().map(admin -> {
            List<Long> ids = roleIdsByAdmin.getOrDefault(admin.getId(), List.of());
            List<String> names = ids.stream().map(roleNames::get).filter(Objects::nonNull).toList();
            return new AdminUserVO(admin.getId(), admin.getAccount(), admin.getRealName(),
                    admin.getStatus(), ids, names, admin.getCreatedAt());
        }).toList();
    }

    private void logOperation(Long adminId, String action, Long targetAdminId, String detail) {
        OperationLog log = new OperationLog();
        log.setOperator("admin#" + adminId);
        log.setModule("ADMIN");
        log.setAction(action);
        log.setTarget(String.valueOf(targetAdminId));
        log.setDetail(detail);
        log.setIp("");
        operationLogMapper.insert(log);
    }
}

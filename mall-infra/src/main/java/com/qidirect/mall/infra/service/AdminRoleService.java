package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.dto.admin.AdminRoleSaveRequest;
import com.qidirect.mall.domain.entity.AdminRole;
import com.qidirect.mall.domain.entity.AdminUserRole;
import com.qidirect.mall.domain.entity.OperationLog;
import com.qidirect.mall.domain.vo.AdminRoleVO;
import com.qidirect.mall.infra.mapper.AdminRoleMapper;
import com.qidirect.mall.infra.mapper.AdminUserRoleMapper;
import com.qidirect.mall.infra.mapper.OperationLogMapper;
import com.qidirect.mall.infra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后台角色管理。perms 以 JSON 数组字符串存储，名称不强制唯一。
 */
@Service
@RequiredArgsConstructor
public class AdminRoleService {

    private final AdminRoleMapper adminRoleMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final OperationLogMapper operationLogMapper;

    public List<AdminRoleVO> list() {
        List<AdminRole> roles = adminRoleMapper.selectList(Wrappers.<AdminRole>lambdaQuery()
                .orderByAsc(AdminRole::getId));
        List<AdminUserRole> links = adminUserRoleMapper.selectList(Wrappers.<AdminUserRole>lambdaQuery());
        Map<Long, Long> userCounts = links.stream().collect(Collectors.groupingBy(
                AdminUserRole::getRoleId, Collectors.counting()));
        return roles.stream().map(role -> new AdminRoleVO(role.getId(), role.getName(),
                parsePerms(role.getPerms()), userCounts.getOrDefault(role.getId(), 0L))).toList();
    }

    @Transactional
    public void create(AdminRoleSaveRequest request) {
        Long adminId = SecurityUtils.currentUserId();
        AdminRole role = new AdminRole();
        apply(role, request);
        adminRoleMapper.insert(role);
        logOperation(adminId, "ROLE_CREATE", role.getId(),
                JSONUtil.createObj()
                        .set("name", role.getName())
                        .set("perms", role.getPerms())
                        .toString());
    }

    /**
     * 删除角色：仍有管理员引用时明确告知，避免删掉后被引用成僵尸权限。
     */
    @Transactional
    public void delete(Long id) {
        AdminRole role = adminRoleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "角色不存在");
        }
        Long used = adminUserRoleMapper.selectCount(Wrappers.<AdminUserRole>lambdaQuery()
                .eq(AdminUserRole::getRoleId, id));
        if (used != null && used > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "该角色下还有 " + used + " 个管理员，请先在账号管理中解除关联");
        }
        adminUserRoleMapper.delete(Wrappers.<AdminUserRole>lambdaQuery()
                .eq(AdminUserRole::getRoleId, id));
        adminRoleMapper.deleteById(id);
        logOperation(SecurityUtils.currentUserId(), "ROLE_DELETE", id,
                JSONUtil.createObj().set("name", role.getName()).toString());
    }

    @Transactional
    public void update(Long id, AdminRoleSaveRequest request) {
        AdminRole role = adminRoleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "角色不存在");
        }
        Long adminId = SecurityUtils.currentUserId();
        apply(role, request);
        adminRoleMapper.updateById(role);
        logOperation(adminId, "ROLE_UPDATE", id,
                JSONUtil.createObj()
                        .set("name", role.getName())
                        .set("perms", role.getPerms())
                        .toString());
    }

    private void apply(AdminRole role, AdminRoleSaveRequest request) {
        role.setName(request.name().trim());
        role.setPerms(JSONUtil.toJsonStr(request.perms()));
    }

    private List<String> parsePerms(String perms) {
        if (StrUtil.isBlank(perms)) {
            return List.of();
        }
        try {
            return JSONUtil.toList(perms, String.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private void logOperation(Long adminId, String action, Long roleId, String detail) {
        OperationLog log = new OperationLog();
        log.setOperator("admin#" + adminId);
        log.setModule("ROLE");
        log.setAction(action);
        log.setTarget(String.valueOf(roleId));
        log.setDetail(detail);
        log.setIp("");
        operationLogMapper.insert(log);
    }
}

package com.qidirect.mall.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qidirect.mall.domain.entity.AdminUser;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AdminUserMapper extends BaseMapper<AdminUser> {

    /**
     * 角色 perms 为 JSON 数组字符串，如 ["*"]。
     */
    @Select("SELECT r.perms FROM admin_role r "
            + "JOIN admin_user_role ur ON ur.role_id = r.id WHERE ur.admin_id = #{adminId}")
    List<String> selectPerms(Long adminId);
}

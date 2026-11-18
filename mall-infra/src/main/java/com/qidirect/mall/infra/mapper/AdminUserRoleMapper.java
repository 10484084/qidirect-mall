package com.qidirect.mall.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qidirect.mall.domain.entity.AdminUserRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AdminUserRoleMapper extends BaseMapper<AdminUserRole> {

    /**
     * 查询某管理员的全部角色 ID。
     */
    @Select("SELECT role_id FROM admin_user_role WHERE admin_id = #{adminId}")
    List<Long> selectRoleIds(@Param("adminId") Long adminId);

    /**
     * 删除某管理员的全部角色关联。
     */
    @Delete("DELETE FROM admin_user_role WHERE admin_id = #{adminId}")
    int deleteByAdminId(@Param("adminId") Long adminId);

    /**
     * 单条写入管理员角色关联。
     */
    @Insert("INSERT INTO admin_user_role (admin_id, role_id) VALUES (#{adminId}, #{roleId})")
    int insertOne(@Param("adminId") Long adminId, @Param("roleId") Long roleId);
}

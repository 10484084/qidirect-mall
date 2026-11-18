package com.qidirect.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 管理员与角色的多对多关联，联合主键为 admin_id + role_id。
 */
@Data
@TableName("admin_user_role")
public class AdminUserRole {

    private Long adminId;

    private Long roleId;
}

package com.qidirect.mall.domain.vo;

import java.util.List;

/**
 * 后台登录返回。
 */
public record AdminLoginVO(
        String token,
        Long adminId,
        String account,
        String realName,
        List<String> perms) {
}

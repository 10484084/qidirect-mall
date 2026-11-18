package com.qidirect.mall.infra.util;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 取真实客户端 IP。生产经 Nginx 反代，优先使用 X-Forwarded-For 首个地址。
 */
public final class ClientIpUtils {

    private ClientIpUtils() {
    }

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(forwarded)) {
            String first = forwarded.split(",")[0].trim();
            if (StrUtil.isNotBlank(first) && !"unknown".equalsIgnoreCase(first)) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StrUtil.isNotBlank(realIp) && !"unknown".equalsIgnoreCase(realIp)) {
            return realIp;
        }
        return StrUtil.nullToEmpty(request.getRemoteAddr());
    }
}

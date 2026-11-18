package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import com.qidirect.mall.domain.entity.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;

/**
 * 门店物化路径缓存：登录时写入，子树查询按缓存前缀展开，改上级生效后清理受影响门店。
 */
@Component
@RequiredArgsConstructor
public class StorePathCache {

    private static final String KEY_PREFIX = "store:path:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;

    /**
     * 优先取缓存；未命中时回填数据库中的路径。
     */
    public String getPath(Store store) {
        String cached = redisTemplate.opsForValue().get(KEY_PREFIX + store.getId());
        if (StrUtil.isNotBlank(cached)) {
            return cached;
        }
        String path = StrUtil.nullToEmpty(store.getPath());
        if (StrUtil.isNotBlank(path)) {
            put(store.getId(), path);
        }
        return path;
    }

    public void put(Long storeId, String path) {
        if (storeId == null || StrUtil.isBlank(path)) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + storeId, path, TTL);
    }

    public void evict(Long storeId) {
        if (storeId == null) {
            return;
        }
        redisTemplate.delete(KEY_PREFIX + storeId);
    }

    public void evictAll(Collection<Long> storeIds) {
        if (storeIds == null || storeIds.isEmpty()) {
            return;
        }
        redisTemplate.delete(storeIds.stream().map(id -> KEY_PREFIX + id).toList());
    }
}

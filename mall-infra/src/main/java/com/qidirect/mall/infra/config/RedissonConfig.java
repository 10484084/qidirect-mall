package com.qidirect.mall.infra.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(@Value("${spring.data.redis.host:127.0.0.1}") String host,
                                         @Value("${spring.data.redis.port:6379}") int port,
                                         @Value("${spring.data.redis.password:}") String password,
                                         @Value("${spring.data.redis.database:0}") int database) {
        Config config = new Config();
        // 延迟建连，避免 Redis 未就绪时阻断应用启动
        config.setLazyInitialization(true);
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                .setPassword(StringUtils.hasText(password) ? password : null);
        return Redisson.create(config);
    }
}

package com.campus.convert.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 下载令牌一次性消费存储（Redis SETNX）。
 * <p>
 * Redis 异常时按“已使用”处理（fail-closed），避免令牌被重放。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DownloadTokenStore {

    private static final String KEY_PREFIX = "campus:download:used:";

    private final StringRedisTemplate redisTemplate;

    public boolean consume(String jti, long ttlMinutes) {
        try {
            Boolean first = redisTemplate.opsForValue()
                    .setIfAbsent(KEY_PREFIX + jti, "1", Duration.ofMinutes(Math.max(1, ttlMinutes)));
            return Boolean.TRUE.equals(first);
        } catch (Exception e) {
            log.error("下载令牌一次性校验失败, jti={}", jti, e);
            return false;
        }
    }
}

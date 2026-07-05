package com.bungae.global.auth.token;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** refresh:{userId} → refreshToken, TTL = refresh 유효기간. 만료·강제 로그아웃 자동 처리. */
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redis;

    public RedisRefreshTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(Long userId, String refreshToken, Duration ttl) {
        redis.opsForValue().set(key(userId), refreshToken, ttl);
    }

    @Override
    public Optional<String> find(Long userId) {
        return Optional.ofNullable(redis.opsForValue().get(key(userId)));
    }

    @Override
    public void delete(Long userId) {
        redis.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}

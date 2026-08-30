package com.tuk.global.auth.token;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** refresh:{userId} → refreshToken, TTL = refresh 유효기간. 만료·강제 로그아웃 자동 처리. */
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";

    /**
     * 비교→교체 or 파기를 한 번에(원자적으로) 수행하는 Lua.
     * 반환: 1=ROTATED, 0=REUSE_DETECTED, -1=NOT_FOUND.
     */
    private static final RedisScript<Long> ROTATE_SCRIPT = RedisScript.of("""
            local cur = redis.call('get', KEYS[1])
            if not cur then return -1 end
            if cur == ARGV[1] then
              redis.call('set', KEYS[1], ARGV[2], 'PX', ARGV[3])
              return 1
            end
            redis.call('del', KEYS[1])
            return 0
            """, Long.class);

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

    @Override
    public RotationResult rotate(Long userId, String presentedToken, String newRefreshToken, Duration ttl) {
        Long code = redis.execute(
                ROTATE_SCRIPT,
                List.of(key(userId)),
                presentedToken, newRefreshToken, String.valueOf(ttl.toMillis()));

        if (code != null && code == 1L) {
            return RotationResult.ROTATED;
        }
        if (code != null && code == 0L) {
            return RotationResult.REUSE_DETECTED;
        }
        return RotationResult.NOT_FOUND;
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}

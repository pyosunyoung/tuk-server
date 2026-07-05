package com.bungae.global.auth.token;

import java.time.Duration;
import java.util.Optional;

/**
 * refresh 토큰 저장소 (사용자당 현재 유효한 refresh 1개).
 * 저장소를 인터페이스로 분리 → AuthService 를 Redis 없이 단위 테스트 가능.
 */
public interface RefreshTokenStore {

    void save(Long userId, String refreshToken, Duration ttl);

    Optional<String> find(Long userId);

    void delete(Long userId);
}

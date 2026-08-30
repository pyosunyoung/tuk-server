package com.tuk.global.auth.token;

import java.time.Duration;
import java.util.Optional;

/**
 * refresh 토큰 저장소.
 *
 * MVP: 사용자당 현재 유효한 refresh 1개(= 단일 세션). 멀티기기 로그인은 지원하지 않는다 —
 * 새 기기 로그인이 이전 기기의 refresh 를 덮어써서 이전 기기는 다음 refresh 시 재로그인해야 한다.
 * 멀티기기가 필요해지면 키를 refresh:{userId}:{deviceId} 로 분리하거나 토큰 집합으로 확장할 것.
 *
 * 저장소를 인터페이스로 분리 → AuthService 를 Redis 없이 단위 테스트 가능.
 */
public interface RefreshTokenStore {

    void save(Long userId, String refreshToken, Duration ttl);

    Optional<String> find(Long userId);

    void delete(Long userId);

    /**
     * refresh 회전을 원자적으로 수행한다(읽기-비교-쓰기 사이 경합 방지).
     *  - 저장본 == presentedToken  → newRefreshToken 으로 교체하고 ROTATED
     *  - 저장본 != presentedToken  → 회전된 옛 토큰 재사용(탈취 의심) → 저장본 파기하고 REUSE_DETECTED
     *  - 저장본 없음               → NOT_FOUND
     */
    RotationResult rotate(Long userId, String presentedToken, String newRefreshToken, Duration ttl);

    enum RotationResult { ROTATED, REUSE_DETECTED, NOT_FOUND }
}

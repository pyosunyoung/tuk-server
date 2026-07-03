package com.bungae.presence.domain;

import com.bungae.global.common.Activity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.util.concurrent.TimeUnit;

/**
 * '지금 한가' 상태. RDB 가 아니라 Redis 에 저장한다.
 *  - key: presence:{userId}
 *  - TTL 로 자동 만료 → 토글을 직접 안 꺼도 시간이 지나면 사라짐('자동 만료' 원칙).
 * spring-boot-starter-data-redis 필요.
 */
@RedisHash("presence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Presence {

    @Id
    private Long userId;

    private Activity activity;

    /** 남은 수명(초). 예: 7200 = 2시간 */
    @TimeToLive(unit = TimeUnit.SECONDS)
    private Long ttlSeconds;

    @Builder
    public Presence(Long userId, Activity activity, Long ttlSeconds) {
        this.userId = userId;
        this.activity = activity;
        this.ttlSeconds = ttlSeconds;
    }
}

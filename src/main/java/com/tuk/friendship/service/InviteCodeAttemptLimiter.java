package com.tuk.friendship.service;

import com.tuk.global.exception.TooManyRequestsException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 초대 코드를 틀리게 넣은 횟수를 사용자별로 센다. 10분에 10번 틀리면 잠시 막는다.
 *
 * 코드만 맞히면 모르는 사람에게 친구 신청이 가므로, 무작위 대입을 막아 둔다.
 * (코드 공간은 31^8 이라 사람 손으론 못 맞히지만, 스크립트로 두드리는 건 막아야 한다.)
 * 제대로 받은 코드를 넣는 사람은 거의 틀리지 않으니 **틀린 경우만** 센다.
 *
 * 창은 첫 실패부터 10분 고정(INCR + 첫 회에만 EXPIRE). refresh 토큰처럼 Redis 에 둔다 —
 * 서버가 재시작되면 카운트가 날아가도 괜찮은 값이다.
 */
@Component
public class InviteCodeAttemptLimiter {

    static final int MAX_FAILURES = 10;
    static final Duration WINDOW = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "invite-code:fail:";

    private final StringRedisTemplate redis;

    public InviteCodeAttemptLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 이미 한도를 넘었으면 429. 코드를 조회하기 전에 부른다. */
    public void checkAllowed(Long userId) {
        String count = redis.opsForValue().get(key(userId));
        if (count != null && Integer.parseInt(count) >= MAX_FAILURES) {
            throw new TooManyRequestsException("코드를 여러 번 잘못 넣었어요. 10분 뒤에 다시 시도해 주세요.");
        }
    }

    /** 없는 코드를 넣었을 때. */
    public void recordFailure(Long userId) {
        String key = key(userId);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, WINDOW);
        }
    }

    private static String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}

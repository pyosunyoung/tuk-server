package com.tuk.friendship.service;

import com.tuk.global.exception.TooManyRequestsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InviteCodeAttemptLimiterTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;
    @InjectMocks InviteCodeAttemptLimiter limiter;

    @Test
    @DisplayName("한도 미만이면 통과, 한도에 닿으면 429")
    void blocksAtLimit() {
        given(redis.opsForValue()).willReturn(ops);

        given(ops.get("invite-code:fail:1")).willReturn(null);
        assertThatCode(() -> limiter.checkAllowed(1L)).doesNotThrowAnyException();

        given(ops.get("invite-code:fail:1")).willReturn(String.valueOf(InviteCodeAttemptLimiter.MAX_FAILURES - 1));
        assertThatCode(() -> limiter.checkAllowed(1L)).doesNotThrowAnyException();

        given(ops.get("invite-code:fail:1")).willReturn(String.valueOf(InviteCodeAttemptLimiter.MAX_FAILURES));
        assertThatThrownBy(() -> limiter.checkAllowed(1L)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    @DisplayName("첫 실패에만 만료 시간을 건다 — 창이 계속 밀리지 않게")
    void expiresOnlyOnFirstFailure() {
        given(redis.opsForValue()).willReturn(ops);

        given(ops.increment("invite-code:fail:1")).willReturn(1L);
        limiter.recordFailure(1L);
        verify(redis).expire("invite-code:fail:1", InviteCodeAttemptLimiter.WINDOW);

        org.mockito.Mockito.clearInvocations(redis);
        given(ops.increment("invite-code:fail:1")).willReturn(2L);
        limiter.recordFailure(1L);
        verify(redis, never()).expire(anyString(), any(java.time.Duration.class));
    }
}

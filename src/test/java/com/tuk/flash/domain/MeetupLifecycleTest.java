package com.tuk.flash.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MeetupLifecycleTest {

    private final LocalDateTime expiresAt = LocalDateTime.of(2026, 9, 28, 20, 0);

    @Test
    @DisplayName("성사 안 된 모임은 약속 시간이 있어도 합류 마감까지만")
    void notFormed() {
        assertThat(MeetupLifecycle.visibleUntil(expiresAt, null, false)).isEqualTo(expiresAt);
        assertThat(MeetupLifecycle.visibleUntil(expiresAt, expiresAt.plusHours(2), false)).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("성사됐고 약속 시간이 있으면 마감과 약속 중 늦은 쪽까지")
    void formedWithMeetAt() {
        assertThat(MeetupLifecycle.visibleUntil(expiresAt, expiresAt.plusHours(2), true))
                .isEqualTo(expiresAt.plusHours(2));
        // 약속을 마감보다 이르게 잡았으면 마감까지는 보인다(아직 새 사람이 들어올 수 있음)
        assertThat(MeetupLifecycle.visibleUntil(expiresAt, expiresAt.minusMinutes(30), true))
                .isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("성사됐는데 약속 시간이 없으면 마감 후 6시간까지")
    void formedWithoutMeetAt() {
        assertThat(MeetupLifecycle.visibleUntil(expiresAt, null, true)).isEqualTo(expiresAt.plusHours(6));
    }
}

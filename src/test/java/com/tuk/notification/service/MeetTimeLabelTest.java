package com.tuk.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MeetTimeLabelTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 9, 29, 18, 10);

    @Test
    @DisplayName("오늘/내일 + 오전·오후 12시간제, 정각이면 분을 생략 (앱 meetTimeLabel 과 같은 모양)")
    void label() {
        assertThat(MeetTimeLabel.of(now.withHour(20).withMinute(0), now)).isEqualTo("오늘 오후 8시");
        assertThat(MeetTimeLabel.of(now.withHour(19).withMinute(30), now)).isEqualTo("오늘 오후 7시 30분");
        assertThat(MeetTimeLabel.of(now.plusDays(1).withHour(0).withMinute(30), now)).isEqualTo("내일 오전 12시 30분");
        assertThat(MeetTimeLabel.of(now.withHour(12).withMinute(0), now)).isEqualTo("오늘 오후 12시");
    }

    @Test
    @DisplayName("바꿈: 같은 날이면 날짜를 한 번만, 날이 바뀌면 둘 다")
    void change() {
        assertThat(MeetTimeLabel.change(now.withHour(19).withMinute(30), now.withHour(20).withMinute(0), now))
                .isEqualTo("오늘 오후 7시 30분 → 오후 8시");
        assertThat(MeetTimeLabel.change(now.withHour(23).withMinute(30), now.plusDays(1).withHour(0).withMinute(0), now))
                .isEqualTo("오늘 오후 11시 30분 → 내일 오전 12시");
    }
}

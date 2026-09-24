package com.tuk.flash.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 모임방 약속 시간 설정.
 *
 * 범위 검증은 서비스에서 한다(@Future 를 쓰면 "8시"를 8시 정각에 누르는 순간 거부되어
 * 쓸 수 없다 — 약간의 과거는 받아준다).
 */
public record SetMeetAtRequest(
        @NotNull(message = "meetAt 은 필수입니다.") LocalDateTime meetAt
) {
}

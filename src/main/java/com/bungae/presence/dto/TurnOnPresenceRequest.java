package com.bungae.presence.dto;

import com.bungae.global.common.Activity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 콜 켜기 요청. activity = 지금 하고 싶은 활동, durationMinutes = 이 상태를 유지할 시간(분). */
public record TurnOnPresenceRequest(
        @NotNull(message = "activity 는 필수입니다.") Activity activity,
        @NotNull(message = "durationMinutes 는 필수입니다.")
        @Min(value = 1, message = "durationMinutes 는 1 이상이어야 합니다.")
        @Max(value = 720, message = "durationMinutes 는 720(12시간) 이하여야 합니다.")
        Integer durationMinutes
) {
}

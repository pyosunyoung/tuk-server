package com.bungae.flash.dto;

import com.bungae.global.common.Activity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 번개 발송 요청.
 *  - scheduledAt: null = 지금, 값이 있으면 예약 시각(미래)
 *  - durationMinutes: 번개가 살아있는 시간(분). 없으면 기본값
 *  - targetUserIds: 초대 대상. null/빈값이면 내 절친 전체. 서버가 절친과 교집합해 재검증한다.
 */
public record SendFlashRequest(
        @NotNull(message = "activity 는 필수입니다.") Activity activity,
        @Size(max = 200, message = "message 는 200자 이하여야 합니다.") String message,
        LocalDateTime scheduledAt,
        @Min(value = 1, message = "durationMinutes 는 1 이상이어야 합니다.")
        @Max(value = 720, message = "durationMinutes 는 720(12시간) 이하여야 합니다.")
        Integer durationMinutes,
        List<Long> targetUserIds
) {
}

package com.tuk.flash.dto;

import com.tuk.global.common.Activity;

import java.time.LocalDateTime;

/** 번개 발송 결과. recipientCount = 서버가 절친 재검증 후 실제로 보낸 대상 수. */
public record FlashResponse(
        Long flashId,
        Long roomId,
        Activity activity,
        LocalDateTime scheduledAt,
        LocalDateTime expiresAt,
        int recipientCount
) {
}

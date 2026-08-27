package com.bungae.flash.dto;

import com.bungae.global.common.Activity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 내가 띄운(발송한) 활성 번개. 홈 상단 "내가 띄운 번개" 배너용.
 * joinedMembers = 합류한 수신자만(보낸 사람 제외). "{N}명 모였어요"는 joinedMembers.size().
 */
public record SentFlashResponse(
        Long flashId,
        Activity activity,
        String message,
        LocalDateTime scheduledAt,
        LocalDateTime expiresAt,
        List<MemberResponse> joinedMembers) {
}

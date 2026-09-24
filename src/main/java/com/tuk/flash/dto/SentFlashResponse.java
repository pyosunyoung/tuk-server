package com.tuk.flash.dto;

import com.tuk.global.common.Activity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 내가 띄운 툭. 홈 "진행 중인 모임" 목록용.
 * expiresAt = 합류 마감, meetAt = 약속 시간(아직 안 정했으면 null) — 서로 다른 시각이다.
 * joinedMembers = 합류한 수신자만(보낸 사람 제외). "{N}명 모였어요"는 joinedMembers.size().
 */
public record SentFlashResponse(
        Long flashId,
        Activity activity,
        String message,
        LocalDateTime scheduledAt,
        LocalDateTime expiresAt,
        LocalDateTime meetAt,
        List<MemberResponse> joinedMembers) {
}

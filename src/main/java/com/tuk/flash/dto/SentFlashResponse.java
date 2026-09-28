package com.tuk.flash.dto;

import com.tuk.global.common.Activity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 내가 띄운 툭. 홈 "진행 중인 모임" 목록용.
 * expiresAt = 합류 마감, meetAt = 약속 시간(아직 안 정했으면 null) — 서로 다른 시각이다.
 * recipientIds = 이 툭을 받은 절친 전부(합류 여부 무관). 앱이 "이 친구들에겐 이미 띄웠어요" 를
 * 알려주는 데 쓴다 — 같은 사람들에게 실수로 두 번 띄우면 모임방이 둘로 갈라진다.
 * joinedMembers = 합류한 수신자만(보낸 사람 제외). "{N}명 모였어요"는 joinedMembers.size().
 * visibleUntil = 목록에서 치울 시각. 앱은 이 값만 보고 지운다(규칙은 MeetupLifecycle).
 */
public record SentFlashResponse(
        Long flashId,
        Activity activity,
        String message,
        LocalDateTime scheduledAt,
        LocalDateTime expiresAt,
        LocalDateTime meetAt,
        LocalDateTime visibleUntil,
        List<Long> recipientIds,
        List<MemberResponse> joinedMembers) {
}

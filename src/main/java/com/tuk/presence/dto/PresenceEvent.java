package com.tuk.presence.dto;

import com.tuk.global.common.Activity;

/**
 * WebSocket 으로 절친에게 보내는 presence 변화 이벤트.
 * userId = 상태가 바뀐 친구. UNAVAILABLE 이면 프로필/활동 필드는 null.
 */
public record PresenceEvent(
        PresenceEventType type,
        Long userId,
        String nickname,
        String profileImage,
        Activity activity,
        Long remainingSeconds
) {
    public static PresenceEvent available(Long userId, String nickname, String profileImage,
                                          Activity activity, Long remainingSeconds) {
        return new PresenceEvent(PresenceEventType.AVAILABLE, userId, nickname, profileImage, activity, remainingSeconds);
    }

    public static PresenceEvent unavailable(Long userId) {
        return new PresenceEvent(PresenceEventType.UNAVAILABLE, userId, null, null, null, null);
    }
}

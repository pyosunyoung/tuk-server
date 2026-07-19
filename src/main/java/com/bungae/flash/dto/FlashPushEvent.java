package com.bungae.flash.dto;

import com.bungae.global.common.Activity;

import java.time.LocalDateTime;

/**
 * WebSocket 으로 보내는 번개 실시간 이벤트(클라이언트는 /user/queue/flash 구독).
 *  - NEW_FLASH: userId=보낸 사람, activity/message/scheduledAt/expiresAt 포함
 *  - MEMBER_JOINED: userId=합류한 사람, 활동/메시지 필드는 null
 */
public record FlashPushEvent(
        FlashPushType type,
        Long flashId,
        Long userId,
        String nickname,
        String profileImage,
        Activity activity,
        String message,
        LocalDateTime scheduledAt,
        LocalDateTime expiresAt
) {
    public static FlashPushEvent newFlash(Long flashId, Long senderId, String nickname, String profileImage,
                                          Activity activity, String message,
                                          LocalDateTime scheduledAt, LocalDateTime expiresAt) {
        return new FlashPushEvent(FlashPushType.NEW_FLASH, flashId, senderId, nickname, profileImage,
                activity, message, scheduledAt, expiresAt);
    }

    public static FlashPushEvent memberJoined(Long flashId, Long joinedUserId, String nickname, String profileImage) {
        return new FlashPushEvent(FlashPushType.MEMBER_JOINED, flashId, joinedUserId, nickname, profileImage,
                null, null, null, null);
    }
}

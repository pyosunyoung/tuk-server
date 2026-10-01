package com.tuk.flash.dto;

import com.tuk.global.common.Activity;

import java.time.LocalDateTime;

/**
 * WebSocket 으로 보내는 번개 실시간 이벤트(클라이언트는 /user/queue/flash 구독).
 *  - NEW_FLASH: userId=보낸 사람, activity/message/scheduledAt/expiresAt 포함
 *  - MEMBER_JOINED: userId=합류한 사람, 활동/메시지 필드는 null
 *  - PLACE_UPDATED: userId=정한 사람, placeBefore(처음이면 null)/placeAfter
 *  - MEET_AT_UPDATED: userId=정한 사람, meetAtBefore(처음이면 null)/meetAtAfter
 *
 * "처음 정함"과 "바꿈"을 따로 알리는 이유: 바뀐 걸 모르면 7시에 옛 장소로 가는 사고가 난다.
 * 그래서 바꿈은 이전 값까지 함께 보낸다.
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
        LocalDateTime expiresAt,
        String placeBefore,
        String placeAfter,
        LocalDateTime meetAtBefore,
        LocalDateTime meetAtAfter
) {
    public static FlashPushEvent newFlash(Long flashId, Long senderId, String nickname, String profileImage,
                                          Activity activity, String message,
                                          LocalDateTime scheduledAt, LocalDateTime expiresAt) {
        return new FlashPushEvent(FlashPushType.NEW_FLASH, flashId, senderId, nickname, profileImage,
                activity, message, scheduledAt, expiresAt, null, null, null, null);
    }

    // 합류·장소·시간 이벤트에도 activity 를 싣는다 — 모임이 여러 개면 알림만 보고 어느 모임인지 알아야 한다.

    public static FlashPushEvent memberJoined(Long flashId, Activity activity,
                                              Long joinedUserId, String nickname, String profileImage) {
        return new FlashPushEvent(FlashPushType.MEMBER_JOINED, flashId, joinedUserId, nickname, profileImage,
                activity, null, null, null, null, null, null, null);
    }

    public static FlashPushEvent placeUpdated(Long flashId, Activity activity,
                                              Long setterId, String nickname, String profileImage,
                                              String before, String after) {
        return new FlashPushEvent(FlashPushType.PLACE_UPDATED, flashId, setterId, nickname, profileImage,
                activity, null, null, null, before, after, null, null);
    }

    public static FlashPushEvent meetAtUpdated(Long flashId, Activity activity,
                                               Long setterId, String nickname, String profileImage,
                                               LocalDateTime before, LocalDateTime after) {
        return new FlashPushEvent(FlashPushType.MEET_AT_UPDATED, flashId, setterId, nickname, profileImage,
                activity, null, null, null, null, null, before, after);
    }
}

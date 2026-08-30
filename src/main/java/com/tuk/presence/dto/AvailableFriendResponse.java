package com.tuk.presence.dto;

import com.tuk.global.common.Activity;

/** 지금 한가한 친구 1명. activity/remainingSeconds 는 Redis presence 에서. */
public record AvailableFriendResponse(
        Long userId,
        String nickname,
        String profileImage,
        Activity activity,
        Long remainingSeconds
) {
}

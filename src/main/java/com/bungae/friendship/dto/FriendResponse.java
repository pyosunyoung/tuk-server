package com.bungae.friendship.dto;

import com.bungae.user.domain.User;

/** 내 친구 목록 1건. */
public record FriendResponse(Long userId, String nickname, String profileImage) {

    public static FriendResponse from(User user) {
        return new FriendResponse(user.getId(), user.getNickname(), user.getProfileImage());
    }
}

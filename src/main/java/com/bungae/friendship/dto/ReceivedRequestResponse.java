package com.bungae.friendship.dto;

import com.bungae.friendship.domain.Friendship;

/** 내가 받은 친구 신청 1건. requester = 신청한 사람. */
public record ReceivedRequestResponse(
        Long friendshipId,
        Long requesterId,
        String nickname,
        String profileImage
) {
    public static ReceivedRequestResponse from(Friendship friendship) {
        return new ReceivedRequestResponse(
                friendship.getId(),
                friendship.getRequester().getId(),
                friendship.getRequester().getNickname(),
                friendship.getRequester().getProfileImage()
        );
    }
}

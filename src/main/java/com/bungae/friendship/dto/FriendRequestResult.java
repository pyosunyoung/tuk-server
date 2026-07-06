package com.bungae.friendship.dto;

import com.bungae.friendship.domain.Friendship;

/** 친구 신청 결과. status = PENDING(신청됨) 또는 ACCEPTED(맞신청으로 즉시 수락). */
public record FriendRequestResult(Long friendshipId, String status) {

    public static FriendRequestResult from(Friendship friendship) {
        return new FriendRequestResult(friendship.getId(), friendship.getStatus().name());
    }
}

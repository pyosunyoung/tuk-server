package com.bungae.friendship.dto;

import jakarta.validation.constraints.NotNull;

/** 친구 신청 요청. addresseeId = 신청 대상 사용자 id. */
public record SendFriendRequest(
        @NotNull(message = "addresseeId 는 필수입니다.") Long addresseeId
) {
}

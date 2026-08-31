package com.tuk.user.dto;

import com.tuk.user.domain.User;

/**
 * 내 정보. 앱이 "이게 나"라고 판단하는 기준점.
 * 모임방 참여자 중 '나' 표시, 장소를 정한 사람이 나인지, 홈 프로필 아바타에 쓴다.
 */
public record MeResponse(Long userId, String nickname, String profileImage) {

    public static MeResponse from(User user) {
        return new MeResponse(user.getId(), user.getNickname(), user.getProfileImage());
    }
}

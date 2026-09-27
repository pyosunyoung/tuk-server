package com.tuk.friendship.dto;

import com.tuk.friendship.domain.Friendship;

/**
 * 내가 보낸 친구 신청 1건(아직 수락 전). addressee = 신청을 받은 사람.
 *
 * 코드를 넣고 나면 "신청을 보냈어요" 토스트 하나가 전부라, 앱을 닫으면 흔적이 없었다.
 * 같은 코드를 또 넣게 되거나 "내가 신청했었나?" 를 알 길이 없어서 목록으로 남긴다.
 *
 * 상대 닉네임을 보여주는 건 괜찮다 — 애초에 그 사람이 나에게 초대 코드를 건넨 사이다.
 */
public record SentRequestResponse(
        Long friendshipId,
        Long addresseeId,
        String nickname,
        String profileImage
) {
    public static SentRequestResponse from(Friendship friendship) {
        return new SentRequestResponse(
                friendship.getId(),
                friendship.getAddressee().getId(),
                friendship.getAddressee().getNickname(),
                friendship.getAddressee().getProfileImage()
        );
    }
}

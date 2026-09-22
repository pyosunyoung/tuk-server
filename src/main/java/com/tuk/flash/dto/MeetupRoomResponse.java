package com.tuk.flash.dto;

import com.tuk.global.common.Activity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 모임방. members = 보낸 사람 + JOINED 인 수신자(거절/미합류자는 노출하지 않음).
 * placeSetById/Nickname = 장소를 정한 멤버(아무도 안 정했으면 둘 다 null).
 * placeKakaoId/placeAddress = 검색으로 고른 장소일 때만 값이 있다(직접 입력이면 null).
 * expiresAt = 번개 만료 시각. 공유 링크로 방에 바로 들어오면 앞선 목록 응답을 못 받으므로 방 응답에 함께 준다.
 */
public record MeetupRoomResponse(
        Long roomId,
        Long flashId,
        Activity activity,
        String placeName,
        Double placeLat,
        Double placeLng,
        String placeKakaoId,
        String placeAddress,
        Long placeSetById,
        String placeSetByNickname,
        String status,
        LocalDateTime expiresAt,
        List<MemberResponse> members
) {
}

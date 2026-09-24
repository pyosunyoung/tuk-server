package com.tuk.flash.dto;

import com.tuk.global.common.Activity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 모임방. members = 보낸 사람 + JOINED 인 수신자(거절/미합류자는 노출하지 않음).
 * placeSetById/Nickname = 장소를 정한 멤버(아무도 안 정했으면 둘 다 null).
 * placeKakaoId/placeAddress = 검색으로 고른 장소일 때만 값이 있다(직접 입력이면 null).
 * meetAt = 만나기로 한 시각(아직 안 정했으면 null). expiresAt 과 다른 시각이다.
 * expiresAt = 모집 마감 시각(언제까지 합류할 수 있나). 약속 시간을 정해도 늘어나지 않는다.
 *             공유 링크로 방에 바로 들어오면 앞선 목록 응답을 못 받으므로 방 응답에 함께 준다.
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
        LocalDateTime meetAt,
        Long meetAtSetById,
        String meetAtSetByNickname,
        String status,
        LocalDateTime expiresAt,
        List<MemberResponse> members
) {
}

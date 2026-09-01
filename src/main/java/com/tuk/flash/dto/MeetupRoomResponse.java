package com.tuk.flash.dto;

import com.tuk.global.common.Activity;

import java.util.List;

/**
 * 모임방. members = 보낸 사람 + JOINED 인 수신자(거절/미합류자는 노출하지 않음).
 * placeSetById/Nickname = 장소를 정한 멤버(아무도 안 정했으면 둘 다 null).
 */
public record MeetupRoomResponse(
        Long roomId,
        Long flashId,
        Activity activity,
        String placeName,
        Double placeLat,
        Double placeLng,
        Long placeSetById,
        String placeSetByNickname,
        String status,
        List<MemberResponse> members
) {
}

package com.bungae.flash.dto;

import com.bungae.global.common.Activity;

import java.util.List;

/** 모임방. members = 보낸 사람 + JOINED 인 수신자(거절/미합류자는 노출하지 않음). */
public record MeetupRoomResponse(
        Long roomId,
        Long flashId,
        Activity activity,
        String placeName,
        Double placeLat,
        Double placeLng,
        String status,
        List<MemberResponse> members
) {
}

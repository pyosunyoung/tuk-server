package com.tuk.flash.dto;

/** 번개 실시간 이벤트 종류. */
public enum FlashPushType {
    NEW_FLASH,       // 새 번개 도착(수신자에게)
    MEMBER_JOINED,   // 누가 합류함(모임방 멤버에게)
    PLACE_UPDATED,   // 장소를 정함/바꿈(정한 사람 뺀 멤버에게) — placeBefore 가 null 이면 처음 정한 것
    MEET_AT_UPDATED  // 약속 시간을 정함/바꿈(정한 사람 뺀 멤버에게) — meetAtBefore 가 null 이면 처음 정한 것
}

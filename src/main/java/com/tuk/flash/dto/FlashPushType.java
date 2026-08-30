package com.tuk.flash.dto;

/** 번개 실시간 이벤트 종류. */
public enum FlashPushType {
    NEW_FLASH,      // 새 번개 도착(수신자에게)
    MEMBER_JOINED   // 누가 합류함(모임방 멤버에게)
}

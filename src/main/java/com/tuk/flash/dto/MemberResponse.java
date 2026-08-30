package com.tuk.flash.dto;

/** 모임방 멤버 1명. sender=true 면 번개를 보낸 사람. */
public record MemberResponse(Long userId, String nickname, String profileImage, boolean sender) {
}

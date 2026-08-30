package com.tuk.friendship.dto;

import jakarta.validation.constraints.NotBlank;

/** 초대코드로 친구 맺기 요청. code = 상대의 초대코드. */
public record RedeemInviteRequest(
        @NotBlank(message = "code 는 필수입니다.") String code
) {
}

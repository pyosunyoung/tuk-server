package com.tuk.global.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** provider: "kakao" | "apple" (대소문자 무관), token: 앱이 소셜 SDK 로 받은 access token. */
public record LoginRequest(
        @NotBlank(message = "provider 는 필수입니다.") String provider,
        @NotBlank(message = "token 은 필수입니다.") String token
) {
}

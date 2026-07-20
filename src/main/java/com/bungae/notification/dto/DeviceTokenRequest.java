package com.bungae.notification.dto;

import jakarta.validation.constraints.NotBlank;

/** 기기 푸시 토큰 해제(로그아웃 등). */
public record DeviceTokenRequest(
        @NotBlank(message = "token 은 필수입니다.") String token
) {
}

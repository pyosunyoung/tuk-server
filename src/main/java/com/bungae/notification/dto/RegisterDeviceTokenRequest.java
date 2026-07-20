package com.bungae.notification.dto;

import com.bungae.notification.domain.DeviceToken.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 기기 푸시 토큰 등록. 앱이 FCM SDK 로 받은 토큰을 올린다. */
public record RegisterDeviceTokenRequest(
        @NotBlank(message = "token 은 필수입니다.") String token,
        @NotNull(message = "platform 은 필수입니다.") Platform platform
) {
}

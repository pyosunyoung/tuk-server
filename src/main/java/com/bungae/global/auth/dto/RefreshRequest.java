package com.bungae.global.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "refreshToken 은 필수입니다.") String refreshToken
) {
}

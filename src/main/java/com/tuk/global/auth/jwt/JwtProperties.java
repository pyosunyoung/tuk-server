package com.tuk.global.auth.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** application.yml 의 jwt.* 바인딩. */
@Getter
@Setter
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** HS256 서명 키. 최소 32바이트. 배포 시 환경변수로 주입. */
    private String secret;

    /** access 토큰 유효기간 (예: 30m). */
    private Duration accessTokenValidity = Duration.ofMinutes(30);

    /** refresh 토큰 유효기간 (예: 14d). */
    private Duration refreshTokenValidity = Duration.ofDays(14);
}

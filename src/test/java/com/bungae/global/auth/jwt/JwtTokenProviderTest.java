package com.bungae.global.auth.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider provider(Duration accessValidity) {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-that-is-at-least-32-bytes-long-0123456789");
        props.setAccessTokenValidity(accessValidity);
        props.setRefreshTokenValidity(Duration.ofDays(14));
        return new JwtTokenProvider(props);
    }

    @Test
    @DisplayName("access 토큰: 유효 + type=access + subject=userId 왕복")
    void accessToken() {
        JwtTokenProvider provider = provider(Duration.ofMinutes(30));
        String token = provider.createAccessToken(42L);

        assertThat(provider.isValid(token)).isTrue();
        assertThat(provider.isAccessToken(token)).isTrue();
        assertThat(provider.isRefreshToken(token)).isFalse();
        assertThat(provider.getUserId(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("refresh 토큰: type=refresh 로 구분된다")
    void refreshToken() {
        JwtTokenProvider provider = provider(Duration.ofMinutes(30));
        String token = provider.createRefreshToken(7L);

        assertThat(provider.isRefreshToken(token)).isTrue();
        assertThat(provider.isAccessToken(token)).isFalse();
        assertThat(provider.getUserId(token)).isEqualTo(7L);
    }

    @Test
    @DisplayName("변조된 토큰(payload 조작)·형식 오류는 isValid=false")
    void tamperedToken() {
        JwtTokenProvider provider = provider(Duration.ofMinutes(30));
        String token = provider.createAccessToken(1L);

        // payload 세그먼트 중간 글자를 바꿔 서명 불일치를 유도
        int dot1 = token.indexOf('.');
        int dot2 = token.indexOf('.', dot1 + 1);
        char[] chars = token.toCharArray();
        int mid = (dot1 + dot2) / 2;
        chars[mid] = (chars[mid] == 'a') ? 'b' : 'a';
        String tampered = new String(chars);

        assertThat(provider.isValid(tampered)).isFalse();
        assertThat(provider.isValid("not-a-jwt")).isFalse();
    }

    @Test
    @DisplayName("다른 secret 으로 서명된 토큰은 검증 실패")
    void differentSecret() {
        String token = provider(Duration.ofMinutes(30)).createAccessToken(1L);

        JwtProperties other = new JwtProperties();
        other.setSecret("completely-different-secret-key-32-bytes-xxxxxxxxxx");
        JwtTokenProvider otherProvider = new JwtTokenProvider(other);

        assertThat(otherProvider.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("만료된 토큰은 isValid=false")
    void expiredToken() throws InterruptedException {
        JwtTokenProvider provider = provider(Duration.ofMillis(1));
        String token = provider.createAccessToken(1L);

        Thread.sleep(20);

        assertThat(provider.isValid(token)).isFalse();
    }
}

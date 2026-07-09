package com.bungae.global.auth;

import com.bungae.global.auth.dto.TokenResponse;
import com.bungae.global.auth.jwt.JwtProperties;
import com.bungae.global.auth.jwt.JwtTokenProvider;
import com.bungae.global.auth.social.OAuthProvider;
import com.bungae.global.auth.social.SocialClientRouter;
import com.bungae.global.auth.social.SocialUserInfo;
import com.bungae.global.auth.token.RefreshTokenStore;
import com.bungae.global.auth.token.RefreshTokenStore.RotationResult;
import com.bungae.global.exception.AuthException;
import com.bungae.user.domain.User;
import com.bungae.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock SocialClientRouter socialClientRouter;
    @Mock UserRepository userRepository;

    JwtTokenProvider tokenProvider;
    InMemoryRefreshTokenStore refreshTokenStore;
    AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-that-is-at-least-32-bytes-long-0123456789");
        props.setAccessTokenValidity(Duration.ofMinutes(30));
        props.setRefreshTokenValidity(Duration.ofDays(14));

        tokenProvider = new JwtTokenProvider(props);
        refreshTokenStore = new InMemoryRefreshTokenStore();
        authService = new AuthService(socialClientRouter, userRepository, tokenProvider, refreshTokenStore, props);
    }

    @Test
    @DisplayName("login: 신규 사용자면 저장 후 토큰 발급, refresh 는 저장소에 보관")
    void loginNewUser() {
        given(socialClientRouter.fetch(eq(OAuthProvider.KAKAO), anyString()))
                .willReturn(new SocialUserInfo("kakao-123", "철수", "img.png"));
        given(userRepository.findByProviderAndProviderId("kakao", "kakao-123"))
                .willReturn(Optional.empty());
        given(userRepository.save(any(User.class)))
                .willAnswer(inv -> withId(inv.getArgument(0), 1L));

        TokenResponse res = authService.login(OAuthProvider.KAKAO, "social-token");

        assertThat(res.accessToken()).isNotBlank();
        assertThat(res.refreshToken()).isNotBlank();
        assertThat(tokenProvider.getUserId(res.accessToken())).isEqualTo(1L);
        assertThat(refreshTokenStore.find(1L)).contains(res.refreshToken());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("login: 기존 사용자면 새로 저장하지 않는다")
    void loginExistingUser() {
        User existing = withId(User.builder()
                .provider("kakao").providerId("kakao-123").nickname("철수").build(), 9L);
        given(socialClientRouter.fetch(eq(OAuthProvider.KAKAO), anyString()))
                .willReturn(new SocialUserInfo("kakao-123", "철수", null));
        given(userRepository.findByProviderAndProviderId("kakao", "kakao-123"))
                .willReturn(Optional.of(existing));

        TokenResponse res = authService.login(OAuthProvider.KAKAO, "social-token");

        assertThat(tokenProvider.getUserId(res.accessToken())).isEqualTo(9L);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("refresh: 저장본과 일치하면 새 쌍 발급 + 저장본 회전(교체)")
    void refreshRotation() {
        String oldRefresh = tokenProvider.createRefreshToken(5L);
        refreshTokenStore.save(5L, oldRefresh, Duration.ofDays(14));

        TokenResponse res = authService.refresh(oldRefresh);

        assertThat(res.refreshToken()).isNotEqualTo(oldRefresh);
        assertThat(refreshTokenStore.find(5L)).contains(res.refreshToken());
    }

    @Test
    @DisplayName("refresh: 저장본과 다른(회전된 옛) 토큰 재사용 시 거부 + 저장본 파기")
    void refreshReuseDetected() {
        String reused = tokenProvider.createRefreshToken(5L);
        refreshTokenStore.save(5L, "current-different-token", Duration.ofDays(14));

        assertThatThrownBy(() -> authService.refresh(reused)).isInstanceOf(AuthException.class);
        assertThat(refreshTokenStore.find(5L)).isEmpty();
    }

    @Test
    @DisplayName("refresh: 저장본이 없으면(만료·로그아웃) 거부")
    void refreshNotFound() {
        String valid = tokenProvider.createRefreshToken(5L); // 서명은 유효하지만 저장소엔 없음

        assertThatThrownBy(() -> authService.refresh(valid)).isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("refresh: access 토큰을 refresh 로 넘기면 거부")
    void refreshWithAccessTokenRejected() {
        String access = tokenProvider.createAccessToken(5L);

        assertThatThrownBy(() -> authService.refresh(access)).isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("logout: 저장된 refresh 를 파기한다")
    void logout() {
        refreshTokenStore.save(3L, "some-token", Duration.ofDays(14));

        authService.logout(3L);

        assertThat(refreshTokenStore.find(3L)).isEmpty();
    }

    private static User withId(User user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    /** Redis 없이 rotation 로직만 검증하기 위한 인메모리 저장소. */
    static class InMemoryRefreshTokenStore implements RefreshTokenStore {
        private final Map<Long, String> store = new HashMap<>();

        @Override public synchronized void save(Long userId, String refreshToken, Duration ttl) {
            store.put(userId, refreshToken);
        }

        @Override public synchronized Optional<String> find(Long userId) {
            return Optional.ofNullable(store.get(userId));
        }

        @Override public synchronized void delete(Long userId) {
            store.remove(userId);
        }

        @Override public synchronized RotationResult rotate(Long userId, String presentedToken,
                                                            String newRefreshToken, Duration ttl) {
            String cur = store.get(userId);
            if (cur == null) {
                return RotationResult.NOT_FOUND;
            }
            if (cur.equals(presentedToken)) {
                store.put(userId, newRefreshToken);
                return RotationResult.ROTATED;
            }
            store.remove(userId);
            return RotationResult.REUSE_DETECTED;
        }
    }
}

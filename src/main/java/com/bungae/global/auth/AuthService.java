package com.bungae.global.auth;

import com.bungae.global.auth.dto.TokenResponse;
import com.bungae.global.auth.jwt.JwtProperties;
import com.bungae.global.auth.jwt.JwtTokenProvider;
import com.bungae.global.auth.social.OAuthProvider;
import com.bungae.global.auth.social.SocialClientRouter;
import com.bungae.global.auth.social.SocialUserInfo;
import com.bungae.global.auth.token.RefreshTokenStore;
import com.bungae.global.exception.AuthException;
import com.bungae.user.domain.User;
import com.bungae.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final SocialClientRouter socialClientRouter;
    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtProperties jwtProperties;

    public AuthService(SocialClientRouter socialClientRouter,
                       UserRepository userRepository,
                       JwtTokenProvider tokenProvider,
                       RefreshTokenStore refreshTokenStore,
                       JwtProperties jwtProperties) {
        this.socialClientRouter = socialClientRouter;
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.jwtProperties = jwtProperties;
    }

    /** 소셜 토큰 검증 → find-or-create User → 자체 JWT 발급. */
    @Transactional
    public TokenResponse login(OAuthProvider provider, String socialAccessToken) {
        SocialUserInfo info = socialClientRouter.fetch(provider, socialAccessToken);

        User user = userRepository.findByProviderAndProviderId(provider.key(), info.providerId())
                .orElseGet(() -> userRepository.save(User.builder()
                        .provider(provider.key())
                        .providerId(info.providerId())
                        .nickname(resolveNickname(info))
                        .profileImage(info.profileImage())
                        .build()));

        return issue(user.getId());
    }

    /** refresh 토큰 rotation: 검증 → 저장본과 대조 → 새 쌍 발급 + 저장본 교체. */
    public TokenResponse refresh(String refreshToken) {
        if (!tokenProvider.isValid(refreshToken) || !tokenProvider.isRefreshToken(refreshToken)) {
            throw new AuthException("유효하지 않은 refresh 토큰입니다.");
        }
        Long userId = tokenProvider.getUserId(refreshToken);

        String stored = refreshTokenStore.find(userId)
                .orElseThrow(() -> new AuthException("만료되었거나 로그아웃된 세션입니다. 다시 로그인하세요."));

        if (!stored.equals(refreshToken)) {
            // 이미 회전된 옛 토큰 재사용 → 탈취 의심. 저장본을 파기해 강제 재로그인.
            refreshTokenStore.delete(userId);
            throw new AuthException("refresh 토큰이 재사용되었습니다. 다시 로그인하세요.");
        }

        return issue(userId);
    }

    public void logout(Long userId) {
        refreshTokenStore.delete(userId);
    }

    private TokenResponse issue(Long userId) {
        String accessToken = tokenProvider.createAccessToken(userId);
        String refreshToken = tokenProvider.createRefreshToken(userId);
        refreshTokenStore.save(userId, refreshToken, jwtProperties.getRefreshTokenValidity());
        return new TokenResponse(accessToken, refreshToken);
    }

    private String resolveNickname(SocialUserInfo info) {
        return (info.nickname() != null && !info.nickname().isBlank())
                ? info.nickname()
                : "번개유저";
    }
}

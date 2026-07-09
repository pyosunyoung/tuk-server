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

    /**
     * refresh 토큰 rotation. 새 토큰을 먼저 만든 뒤, 저장소에서 비교→교체를 원자적으로 수행한다.
     * (같은 토큰으로 동시 요청이 와도 하나만 ROTATED, 나머지는 REUSE 로 안전하게 처리)
     */
    public TokenResponse refresh(String refreshToken) {
        if (!tokenProvider.isValid(refreshToken) || !tokenProvider.isRefreshToken(refreshToken)) {
            throw new AuthException("유효하지 않은 refresh 토큰입니다.");
        }
        Long userId = tokenProvider.getUserId(refreshToken);
        String newAccessToken = tokenProvider.createAccessToken(userId);
        String newRefreshToken = tokenProvider.createRefreshToken(userId);

        RotationResult result = refreshTokenStore.rotate(
                userId, refreshToken, newRefreshToken, jwtProperties.getRefreshTokenValidity());

        return switch (result) {
            case ROTATED -> new TokenResponse(newAccessToken, newRefreshToken);
            case REUSE_DETECTED -> throw new AuthException("refresh 토큰이 재사용되었습니다. 다시 로그인하세요.");
            case NOT_FOUND -> throw new AuthException("만료되었거나 로그아웃된 세션입니다. 다시 로그인하세요.");
        };
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

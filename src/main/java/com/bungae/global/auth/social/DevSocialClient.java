package com.bungae.global.auth.social;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 개발/스모크 테스트 전용 소셜 로그인. dev 프로필에서만 빈으로 등록된다.
 * 외부 검증 없이 token 을 그대로 providerId 로 사용 → 같은 token = 같은 유저.
 * 예: POST /api/auth/login {"provider":"dev","token":"1"} → provider=dev, providerId=1 유저로 로그인.
 */
@Profile("dev")
@Component
public class DevSocialClient implements SocialClient {

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.DEV;
    }

    @Override
    public SocialUserInfo fetch(String socialAccessToken) {
        return new SocialUserInfo(socialAccessToken, "dev-" + socialAccessToken, null);
    }
}

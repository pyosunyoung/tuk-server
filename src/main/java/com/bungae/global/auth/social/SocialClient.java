package com.bungae.global.auth.social;

/**
 * 소셜 제공자별 토큰 검증 클라이언트. 제공자마다 구현체 하나.
 * SocialClientRouter 가 provider() 로 구현체를 고른다.
 */
public interface SocialClient {

    OAuthProvider provider();

    /** 앱이 넘긴 소셜 access token 을 제공자에게 검증하고 사용자 정보를 가져온다. */
    SocialUserInfo fetch(String socialAccessToken);
}

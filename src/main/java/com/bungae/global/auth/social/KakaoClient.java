package com.bungae.global.auth.social;

import com.bungae.global.exception.AuthException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 카카오 토큰 검증: 앱이 넘긴 access token 으로 카카오 사용자 정보 API 호출.
 * GET https://kapi.kakao.com/v2/user/me  (Authorization: Bearer &lt;token&gt;)
 */
@Component
public class KakaoClient implements SocialClient {

    private final RestClient restClient;

    public KakaoClient() {
        // RestClient.Builder 는 Boot 4 에서 자동 구성 빈이 아니므로 정적 팩토리로 직접 생성
        this.restClient = RestClient.builder()
                .baseUrl("https://kapi.kakao.com")
                .build();
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.KAKAO;
    }

    @Override
    public SocialUserInfo fetch(String socialAccessToken) { // 카카오 서버의 해당 토큰의 유저 정보 조회 로직
        KakaoUserResponse res = restClient.get()
                .uri("/v2/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + socialAccessToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new AuthException("카카오 토큰 검증에 실패했습니다.");
                })
                .body(KakaoUserResponse.class);

        if (res == null || res.id() == null) {
            throw new AuthException("카카오 사용자 정보를 가져오지 못했습니다.");
        }

        String nickname = null;
        String profileImage = null;
        if (res.kakao_account() != null && res.kakao_account().profile() != null) {
            nickname = res.kakao_account().profile().nickname();
            profileImage = res.kakao_account().profile().profile_image_url();
        }
        return new SocialUserInfo(String.valueOf(res.id()), nickname, profileImage);
    }

    /** 카카오 응답 매핑 (필요한 필드만). snake_case 그대로 받아 Jackson 이 매핑. */
    private record KakaoUserResponse(Long id, KakaoAccount kakao_account) {
        private record KakaoAccount(Profile profile) {
        }

        private record Profile(String nickname, String profile_image_url) {
        }
    }
}

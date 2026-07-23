package com.bungae.global.auth.social;

import com.bungae.global.exception.AuthException;
import com.bungae.global.exception.ExternalApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 카카오 토큰 검증: 앱이 넘긴 access token 으로 카카오 사용자 정보 API 호출.
 * GET {kakao.api-base-url}/v2/user/me  (Authorization: Bearer &lt;token&gt;)
 *
 * 에러 구분:
 *  - 4xx(토큰 무효/만료)      → AuthException(401)         : 사용자 잘못
 *  - 5xx / 연결 실패 / 타임아웃 → ExternalApiException(502)  : 카카오 쪽 문제, 우리 잘못 아님
 */
@Component
public class KakaoClient implements SocialClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;

    public KakaoClient(@Value("${kakao.api-base-url:https://kapi.kakao.com}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.KAKAO;
    }

    @Override
    public SocialUserInfo fetch(String socialAccessToken) {
        KakaoUserResponse res;
        try {
            res = restClient.get()
                    .uri("/v2/user/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + socialAccessToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        throw new AuthException("유효하지 않은 카카오 토큰입니다.");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new ExternalApiException("카카오 인증 서버 오류입니다. 잠시 후 다시 시도해주세요.");
                    })
                    .body(KakaoUserResponse.class);
        } catch (ResourceAccessException e) {
            throw new ExternalApiException("카카오 인증 서버에 연결할 수 없습니다.");
        }

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

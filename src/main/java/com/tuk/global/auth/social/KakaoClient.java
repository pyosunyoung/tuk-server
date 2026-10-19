package com.tuk.global.auth.social;

import com.tuk.global.exception.AuthException;
import com.tuk.global.exception.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 카카오 토큰 검증: 앱이 넘긴 access token 이
 *  1) 툭 앱에서 발급된 토큰인지   GET /v1/user/access_token_info → app_id 비교
 *  2) 누구의 토큰인지             GET /v2/user/me
 * 를 차례로 확인한다.
 *
 * 1) 이 없으면 다른 카카오 앱에서 발급받은 같은 사용자의 토큰으로도 툭에 그 사용자로 로그인된다(토큰 치환).
 * app-id(KAKAO_APP_ID)가 비어 있으면 1) 을 건너뛰고 기동 시 경고만 남긴다 — 설정 전 배포로 로그인이 막히지 않게.
 *
 * 에러 구분:
 *  - 4xx(토큰 무효/만료) · 다른 앱 토큰 → AuthException(401)        : 사용자 잘못
 *  - 5xx / 연결 실패 / 타임아웃         → ExternalApiException(502) : 카카오 쪽 문제, 우리 잘못 아님
 */
@Component
public class KakaoClient implements SocialClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;
    private final Long appId;

    public KakaoClient(@Value("${kakao.api-base-url:https://kapi.kakao.com}") String baseUrl,
                       @Value("${kakao.app-id:}") String appId) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.appId = appId == null || appId.isBlank() ? null : Long.valueOf(appId.trim());
        if (this.appId == null) {
            log.warn("kakao.app-id(KAKAO_APP_ID) 미설정 — 카카오 토큰의 발급 앱을 확인하지 않습니다");
        }
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.KAKAO;
    }

    @Override
    public SocialUserInfo fetch(String socialAccessToken) {
        if (appId != null) {
            verifyIssuedForThisApp(socialAccessToken);
        }

        KakaoUserResponse res = get("/v2/user/me", socialAccessToken, KakaoUserResponse.class);
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

    private void verifyIssuedForThisApp(String socialAccessToken) {
        TokenInfoResponse info = get("/v1/user/access_token_info", socialAccessToken, TokenInfoResponse.class);
        if (info == null || !appId.equals(info.app_id())) {
            log.warn("다른 앱에서 발급된 카카오 토큰 거부: app_id={}", info == null ? null : info.app_id());
            throw new AuthException("유효하지 않은 카카오 토큰입니다.");
        }
    }

    private <T> T get(String uri, String socialAccessToken, Class<T> type) {
        try {
            return restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + socialAccessToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        throw new AuthException("유효하지 않은 카카오 토큰입니다.");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new ExternalApiException("카카오 인증 서버 오류입니다. 잠시 후 다시 시도해주세요.");
                    })
                    .body(type);
        } catch (ResourceAccessException e) {
            throw new ExternalApiException("카카오 인증 서버에 연결할 수 없습니다.");
        }
    }

    /** 카카오 응답 매핑 (필요한 필드만). snake_case 그대로 받아 Jackson 이 매핑. */
    private record KakaoUserResponse(Long id, KakaoAccount kakao_account) {
        private record KakaoAccount(Profile profile) {
        }

        private record Profile(String nickname, String profile_image_url) {
        }
    }

    private record TokenInfoResponse(Long id, Long app_id) {
    }
}

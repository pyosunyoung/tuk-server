package com.tuk.global.auth.social;

import com.sun.net.httpserver.HttpServer;
import com.tuk.global.exception.AuthException;
import com.tuk.global.exception.ExternalApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 카카오 대신 JDK 내장 HTTP 서버로 두 API(토큰 정보 · 사용자 정보)를 흉내 낸다.
 * 핵심은 "다른 카카오 앱에서 발급된 토큰"을 거부하는지.
 */
class KakaoClientTest {

    private static final long TUK_APP_ID = 1234567L;

    private HttpServer server;
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private volatile int tokenInfoStatus = 200;
    private volatile long tokenAppId = TUK_APP_ID;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/user/access_token_info", exchange -> {
            calls.add("token_info " + exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, tokenInfoStatus, """
                    {"id":42,"expires_in":7199,"app_id":%d}""".formatted(tokenAppId));
        });
        server.createContext("/v2/user/me", exchange -> {
            calls.add("me " + exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                    {"id":42,"kakao_account":{"profile":{"nickname":"민수","profile_image_url":"https://img/p.png"}}}""");
        });
        server.start();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private KakaoClient client(String appId) {
        return new KakaoClient("http://127.0.0.1:" + server.getAddress().getPort(), appId);
    }

    @Test
    @DisplayName("툭 앱에서 발급된 토큰이면 발급 앱을 먼저 확인하고 사용자 정보를 매핑한다")
    void acceptsTokenIssuedForThisApp() {
        SocialUserInfo info = client(String.valueOf(TUK_APP_ID)).fetch("kakao-token");

        assertThat(info.providerId()).isEqualTo("42");
        assertThat(info.nickname()).isEqualTo("민수");
        assertThat(calls).containsExactly("token_info Bearer kakao-token", "me Bearer kakao-token");
    }

    @Test
    @DisplayName("다른 카카오 앱에서 발급된 토큰은 거부하고 사용자 정보는 조회하지 않는다")
    void rejectsTokenIssuedForAnotherApp() {
        tokenAppId = 9999999L;

        assertThatThrownBy(() -> client(String.valueOf(TUK_APP_ID)).fetch("other-app-token"))
                .isInstanceOf(AuthException.class);
        assertThat(calls).containsExactly("token_info Bearer other-app-token");
    }

    @Test
    @DisplayName("토큰 정보 조회가 4xx 면 401, 5xx 면 502")
    void mapsTokenInfoErrors() {
        tokenInfoStatus = 401;
        assertThatThrownBy(() -> client(String.valueOf(TUK_APP_ID)).fetch("expired"))
                .isInstanceOf(AuthException.class);

        tokenInfoStatus = 500;
        assertThatThrownBy(() -> client(String.valueOf(TUK_APP_ID)).fetch("any"))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("앱 ID 설정이 비어 있으면 발급 앱 확인을 건너뛴다(설정 전 배포로 로그인이 막히지 않게)")
    void skipsAppCheckWhenNotConfigured() {
        tokenAppId = 9999999L;

        SocialUserInfo info = client("").fetch("kakao-token");

        assertThat(info.providerId()).isEqualTo("42");
        assertThat(calls).containsExactly("me Bearer kakao-token");
    }
}

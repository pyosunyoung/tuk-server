package com.tuk.place.client;

import com.sun.net.httpserver.HttpServer;
import com.tuk.global.exception.ExternalApiException;
import com.tuk.place.client.KakaoLocalClient.KakaoPlace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 카카오 대신 JDK 내장 HTTP 서버로 응답을 흉내 내, 요청 형식(헤더·쿼리)과 응답 매핑·에러 처리를 검증한다.
 */
class KakaoLocalClientTest {

    private HttpServer server;
    private final AtomicReference<String> authHeader = new AtomicReference<>();
    private final AtomicReference<String> rawQuery = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String body = "{}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/local/search/keyword.json", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private KakaoLocalClient client(String key) {
        return new KakaoLocalClient("http://127.0.0.1:" + server.getAddress().getPort(), key);
    }

    @Test
    @DisplayName("KakaoAK 헤더로 요청하고, 한글 검색어를 인코딩해 보내며, 응답을 좌표 숫자로 매핑한다")
    void mapsResponse() {
        body = """
                {"documents":[{
                  "id":"26338954","place_name":"스타벅스 강남R점",
                  "category_name":"음식점 > 카페 > 커피전문점 > 스타벅스","category_group_name":"카페",
                  "address_name":"서울 강남구 역삼동 825","road_address_name":"서울 강남구 강남대로 390",
                  "x":"127.028","y":"37.4979"}]}
                """;

        List<KakaoPlace> places = client("test-key").searchKeyword("강남역 스타벅스");

        assertThat(authHeader.get()).isEqualTo("KakaoAK test-key");
        assertThat(URLDecoder.decode(rawQuery.get(), StandardCharsets.UTF_8)).contains("query=강남역 스타벅스");
        assertThat(places).singleElement().satisfies(p -> {
            assertThat(p.id()).isEqualTo("26338954");
            assertThat(p.name()).isEqualTo("스타벅스 강남R점");
            assertThat(p.roadAddress()).isEqualTo("서울 강남구 강남대로 390");
            assertThat(p.lat()).isEqualTo(37.4979);   // y = 위도
            assertThat(p.lng()).isEqualTo(127.028);   // x = 경도
        });
    }

    @Test
    @DisplayName("좌표가 없거나 숫자가 아닌 결과는 버린다(지도에 찍을 수 없으니)")
    void dropsResultsWithoutCoordinates() {
        body = """
                {"documents":[
                  {"id":"1","place_name":"좌표없음","x":null,"y":null},
                  {"id":"2","place_name":"깨진좌표","x":"abc","y":"37.1"},
                  {"id":"3","place_name":"정상","x":"127.0","y":"37.5"}]}
                """;

        assertThat(client("k").searchKeyword("q")).extracting(KakaoPlace::name).containsExactly("정상");
    }

    @Test
    @DisplayName("키가 틀렸거나 카카오맵 사용 설정이 꺼져 401/403 이면 502 로 감싼다")
    void unauthorizedBecomesBadGateway() {
        status = 401;
        body = "{\"errorType\":\"AccessDeniedError\"}";

        assertThatThrownBy(() -> client("wrong").searchKeyword("q"))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("카카오 서버 오류도 502")
    void serverErrorBecomesBadGateway() {
        status = 500;

        assertThatThrownBy(() -> client("k").searchKeyword("q"))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("키가 설정되지 않았으면 카카오를 부르지 않고 바로 502 — 앱은 직접 입력으로 대체")
    void missingKeyDoesNotCallKakao() {
        KakaoLocalClient noKey = client("  ");

        assertThat(noKey.isConfigured()).isFalse();
        assertThatThrownBy(() -> noKey.searchKeyword("q")).isInstanceOf(ExternalApiException.class);
        assertThat(authHeader.get()).isNull();
    }
}

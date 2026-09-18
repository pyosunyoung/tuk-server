package com.tuk.place.client;

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
import java.util.List;

/**
 * 카카오 로컬 API — 키워드로 장소 검색.
 * GET {kakao.local-base-url}/v2/local/search/keyword.json?query=...  (Authorization: KakaoAK &lt;REST API 키&gt;)
 *
 * REST API 키는 서버에만 둔다(앱에 넣으면 누구나 꺼내 쓸 수 있다). 앱은 우리 /api/places/search 를 부른다.
 *
 * 에러는 전부 ExternalApiException(502) — 사용자가 잘못한 게 아니다.
 * 401/403 은 키가 틀렸거나 콘솔에서 카카오맵 사용 설정이 꺼진 경우라 운영자가 알아야 해서 경고 로그를 남긴다.
 */
@Component
public class KakaoLocalClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoLocalClient.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    /** 한 번에 보여줄 결과 수. 카카오 최대 15. */
    private static final int SIZE = 15;

    private final RestClient restClient;
    private final String restApiKey;

    public KakaoLocalClient(
            @Value("${kakao.local-base-url:https://dapi.kakao.com}") String baseUrl,
            @Value("${kakao.rest-api-key:}") String restApiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.restApiKey = restApiKey == null ? "" : restApiKey.trim();
    }

    public boolean isConfigured() {
        return !restApiKey.isEmpty();
    }

    public List<KakaoPlace> searchKeyword(String query) {
        if (!isConfigured()) {
            throw new ExternalApiException("장소 검색을 지금은 쓸 수 없어요. 직접 입력해 주세요.");
        }
        KeywordResponse res;
        try {
            res = restClient.get()
                    .uri(b -> b.path("/v2/local/search/keyword.json")
                            .queryParam("query", query)
                            .queryParam("size", SIZE)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
                    .retrieve()
                    .onStatus(s -> s.value() == 401 || s.value() == 403, (request, response) -> {
                        log.warn("카카오 로컬 API 인증 실패 status={} — REST API 키 또는 콘솔의 카카오맵 사용 설정을 확인",
                                response.getStatusCode().value());
                        throw new ExternalApiException("장소 검색을 지금은 쓸 수 없어요. 직접 입력해 주세요.");
                    })
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        log.warn("카카오 로컬 API 오류 status={}", response.getStatusCode().value());
                        throw new ExternalApiException("장소 검색에 실패했어요. 잠시 후 다시 시도해 주세요.");
                    })
                    .body(KeywordResponse.class);
        } catch (ResourceAccessException e) {
            throw new ExternalApiException("장소 검색 서버에 연결할 수 없어요.");
        }
        if (res == null || res.documents() == null) {
            return List.of();
        }
        return res.documents().stream()
                .filter(d -> d.id() != null && d.x() != null && d.y() != null)
                .map(d -> new KakaoPlace(
                        d.id(),
                        d.place_name(),
                        d.road_address_name(),
                        d.address_name(),
                        d.category_name(),
                        d.category_group_name(),
                        parse(d.y()),
                        parse(d.x())))
                .filter(p -> p.lat() != null && p.lng() != null)
                .toList();
    }

    private static Double parse(String v) {
        try {
            return Double.valueOf(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 카카오 응답에서 필요한 값만. 좌표는 x=경도, y=위도(문자열). */
    public record KakaoPlace(
            String id,
            String name,
            String roadAddress,
            String address,
            String categoryName,
            String categoryGroupName,
            Double lat,
            Double lng
    ) {
    }

    private record KeywordResponse(List<Document> documents) {
        private record Document(
                String id,
                String place_name,
                String category_name,
                String category_group_name,
                String address_name,
                String road_address_name,
                String x,
                String y
        ) {
        }
    }
}

package com.tuk.place.service;

import com.tuk.global.exception.BadRequestException;
import com.tuk.place.client.KakaoLocalClient;
import com.tuk.place.client.KakaoLocalClient.KakaoPlace;
import com.tuk.place.dto.PlaceSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PlaceSearchServiceTest {

    @Mock
    KakaoLocalClient kakaoLocalClient;

    @InjectMocks
    PlaceSearchService placeSearchService;

    @Test
    @DisplayName("검색어 앞뒤 공백을 잘라 넘기고, 도로명 주소·분류 그룹을 골라 돌려준다")
    void mapsResult() {
        given(kakaoLocalClient.searchKeyword("홍대 카페")).willReturn(List.of(
                new KakaoPlace("1", "A카페", "서울 마포구 와우산로 1", "서울 마포구 서교동 1",
                        "음식점 > 카페 > 커피전문점 > A카페", "카페", 37.55, 126.92)));

        List<PlaceSearchResult> res = placeSearchService.search("  홍대 카페 ");

        assertThat(res).singleElement().satisfies(r -> {
            assertThat(r.kakaoPlaceId()).isEqualTo("1");
            assertThat(r.address()).isEqualTo("서울 마포구 와우산로 1");
            assertThat(r.category()).isEqualTo("카페");
            assertThat(r.lat()).isEqualTo(37.55);
        });
    }

    @Test
    @DisplayName("도로명 주소가 없으면 지번, 분류 그룹이 없으면 분류의 마지막 단계")
    void fallbacks() {
        given(kakaoLocalClient.searchKeyword("정문")).willReturn(List.of(
                new KakaoPlace("2", "학교 정문", "", "서울 성북구 안암동 1", "교육,학문 > 학교 > 대학교", "", 37.58, 127.03)));

        PlaceSearchResult r = placeSearchService.search("정문").get(0);

        assertThat(r.address()).isEqualTo("서울 성북구 안암동 1");
        assertThat(r.category()).isEqualTo("대학교");
    }

    @Test
    @DisplayName("빈 검색어·너무 긴 검색어는 카카오를 부르지 않고 400")
    void rejectsBadQuery() {
        assertThatThrownBy(() -> placeSearchService.search("   ")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> placeSearchService.search("가".repeat(PlaceSearchService.MAX_QUERY_LENGTH + 1)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(kakaoLocalClient);
    }
}

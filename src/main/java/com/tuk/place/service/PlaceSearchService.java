package com.tuk.place.service;

import com.tuk.global.exception.BadRequestException;
import com.tuk.place.client.KakaoLocalClient;
import com.tuk.place.client.KakaoLocalClient.KakaoPlace;
import com.tuk.place.dto.PlaceSearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

/** 모임방 장소 정하기용 장소 검색. 외부 API 는 KakaoLocalClient 가 맡는다. */
@Service
public class PlaceSearchService {

    static final int MAX_QUERY_LENGTH = 50;

    private final KakaoLocalClient kakaoLocalClient;

    public PlaceSearchService(KakaoLocalClient kakaoLocalClient) {
        this.kakaoLocalClient = kakaoLocalClient;
    }

    public List<PlaceSearchResult> search(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.strip();
        if (query.isEmpty()) {
            throw new BadRequestException("검색어를 입력해 주세요.");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new BadRequestException("검색어는 " + MAX_QUERY_LENGTH + "자 이하로 입력해 주세요.");
        }
        return kakaoLocalClient.searchKeyword(query).stream()
                .map(PlaceSearchService::toResult)
                .toList();
    }

    private static PlaceSearchResult toResult(KakaoPlace p) {
        String address = notBlank(p.roadAddress()) ? p.roadAddress() : p.address();
        return new PlaceSearchResult(p.id(), p.name(), address, category(p), p.lat(), p.lng());
    }

    /** "음식점 > 카페 > 커피전문점 > 스타벅스" → "스타벅스" 는 가게명이라 부적절 → 그룹명("카페") 우선, 없으면 마지막 단계. */
    private static String category(KakaoPlace p) {
        if (notBlank(p.categoryGroupName())) {
            return p.categoryGroupName();
        }
        if (!notBlank(p.categoryName())) {
            return null;
        }
        String[] parts = p.categoryName().split(">");
        return parts[parts.length - 1].strip();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

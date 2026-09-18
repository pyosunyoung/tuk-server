package com.tuk.place.dto;

/**
 * 장소 검색 결과 한 건. 앱은 이 값을 그대로 모임방 장소로 저장한다(PATCH /api/flashes/{id}/room/place).
 *
 * @param kakaoPlaceId 카카오 장소 ID — 지도 앱에서 "그 가게 페이지"를 바로 여는 데 쓴다
 * @param address      도로명 주소, 없으면 지번 주소
 * @param category     "카페", "음식점"처럼 사람이 읽는 분류(카카오 분류의 마지막 단계)
 */
public record PlaceSearchResult(
        String kakaoPlaceId,
        String name,
        String address,
        String category,
        Double lat,
        Double lng
) {
}

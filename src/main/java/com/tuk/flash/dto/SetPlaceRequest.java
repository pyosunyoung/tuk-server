package com.tuk.flash.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 모임방 장소 설정.
 * - 직접 입력: placeName 만 (나머지 null)
 * - 검색으로 선택: placeName + 좌표 + kakaoPlaceId + placeAddress
 */
public record SetPlaceRequest(
        @NotBlank(message = "placeName 은 필수입니다.")
        @Size(max = 100, message = "placeName 은 100자 이하여야 합니다.")
        String placeName,
        Double placeLat,
        Double placeLng,
        @Pattern(regexp = "[0-9]{1,30}", message = "kakaoPlaceId 형식이 올바르지 않습니다.")
        String kakaoPlaceId,
        @Size(max = 255, message = "placeAddress 는 255자 이하여야 합니다.")
        String placeAddress
) {

    /** 직접 입력용(이름만). */
    public SetPlaceRequest(String placeName) {
        this(placeName, null, null, null, null);
    }

    /** 검색 장소라면 지도에 핀을 찍을 수 있게 좌표가 반드시 함께 와야 한다. */
    @AssertTrue(message = "검색으로 고른 장소는 좌표가 필요합니다.")
    public boolean isCoordinatesPresentForSearchedPlace() {
        return kakaoPlaceId == null || (placeLat != null && placeLng != null);
    }
}

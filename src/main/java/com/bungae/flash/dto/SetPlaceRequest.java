package com.bungae.flash.dto;

import jakarta.validation.constraints.NotBlank;

/** 모임방 장소 설정. lat/lng 는 선택. */
public record SetPlaceRequest(
        @NotBlank(message = "placeName 은 필수입니다.") String placeName,
        Double placeLat,
        Double placeLng
) {
}

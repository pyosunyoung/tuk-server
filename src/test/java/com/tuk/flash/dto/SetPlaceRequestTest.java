package com.tuk.flash.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SetPlaceRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<SetPlaceRequest>> validate(SetPlaceRequest r) {
        return validator.validate(r);
    }

    @Test
    @DisplayName("직접 입력(이름만)은 통과")
    void manualOk() {
        assertThat(validate(new SetPlaceRequest("학교 정문"))).isEmpty();
    }

    @Test
    @DisplayName("검색 장소(이름·좌표·ID·주소)는 통과")
    void searchedOk() {
        assertThat(validate(new SetPlaceRequest("A카페", 37.5, 127.0, "26338954", "서울 강남구"))).isEmpty();
    }

    @Test
    @DisplayName("카카오 장소 ID 가 있는데 좌표가 없으면 거부 — 지도에 핀을 못 찍는다")
    void searchedWithoutCoordinates() {
        assertThat(validate(new SetPlaceRequest("A카페", null, null, "26338954", null))).isNotEmpty();
    }

    @Test
    @DisplayName("카카오 장소 ID 는 숫자만")
    void kakaoIdDigitsOnly() {
        assertThat(validate(new SetPlaceRequest("A카페", 37.5, 127.0, "abc; DROP", null))).isNotEmpty();
    }

    @Test
    @DisplayName("이름이 비었거나 100자를 넘으면 거부")
    void nameRules() {
        assertThat(validate(new SetPlaceRequest("  "))).isNotEmpty();
        assertThat(validate(new SetPlaceRequest("가".repeat(101)))).isNotEmpty();
    }
}

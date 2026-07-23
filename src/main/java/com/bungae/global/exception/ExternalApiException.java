package com.bungae.global.exception;

import org.springframework.http.HttpStatus;

/** 외부 API(카카오 등) 장애·연결 실패 → 502. 사용자 토큰 문제(401)와 구분한다. */
public class ExternalApiException extends BusinessException {

    public ExternalApiException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }
}

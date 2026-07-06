package com.bungae.global.exception;

import org.springframework.http.HttpStatus;

/** 잘못된 요청/규칙 위반 → 400. */
public class BadRequestException extends BusinessException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}

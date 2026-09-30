package com.tuk.global.exception;

import org.springframework.http.HttpStatus;

/** 짧은 시간에 너무 많이 시도함 → 429. */
public class TooManyRequestsException extends BusinessException {

    public TooManyRequestsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}

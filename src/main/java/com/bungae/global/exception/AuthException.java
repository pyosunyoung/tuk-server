package com.bungae.global.exception;

/** 인증/인가 관련 실패. GlobalExceptionHandler 에서 401 로 응답. */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }
}

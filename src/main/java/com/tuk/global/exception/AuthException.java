package com.tuk.global.exception;

import org.springframework.http.HttpStatus;

/** 인증/인가 실패 → 401. */
public class AuthException extends BusinessException {

    public AuthException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}

package com.tuk.global.exception;

import org.springframework.http.HttpStatus;

/** 권한 없음 → 403. */
public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}

package com.bungae.global.exception;

import org.springframework.http.HttpStatus;

/** 리소스가 없음 → 404. */
public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}

package com.tuk.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 도메인/비즈니스 규칙 위반의 공통 베이스. 상태코드를 담아 GlobalExceptionHandler 가 한 곳에서 처리한다.
 */
public abstract class BusinessException extends RuntimeException {

    private final HttpStatus status;

    protected BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

package com.bungae.global.auth.social;

import com.bungae.global.exception.AuthException;

/** 지원하는 소셜 로그인 제공자. User.provider 에는 key()(소문자)로 저장한다. */
public enum OAuthProvider {
    KAKAO,
    APPLE;

    /** User.provider 컬럼에 저장/조회할 값. 예: "kakao" */
    public String key() {
        return name().toLowerCase();
    }

    public static OAuthProvider from(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AuthException("지원하지 않는 소셜 로그인 제공자입니다: " + value);
        }
    }
}

package com.tuk.global.auth.social;

/** 소셜 제공자에게서 검증해 얻은 사용자 정보. */
public record SocialUserInfo(String providerId, String nickname, String profileImage) {
}

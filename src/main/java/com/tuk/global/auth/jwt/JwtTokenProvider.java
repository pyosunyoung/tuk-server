package com.tuk.global.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * 자체 JWT 발급·검증. subject = userId, "type" 클레임으로 access/refresh 구분.
 */
@Component
public class JwtTokenProvider {

    private static final String TYPE_CLAIM = "type";
    static final String TYPE_ACCESS = "access";
    static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessValidityMs;
    private final long refreshValidityMs;

    public JwtTokenProvider(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
        this.accessValidityMs = props.getAccessTokenValidity().toMillis();
        this.refreshValidityMs = props.getRefreshTokenValidity().toMillis();
    }

    public String createAccessToken(Long userId) {
        return create(userId, TYPE_ACCESS, accessValidityMs);
    }

    public String createRefreshToken(Long userId) {
        return create(userId, TYPE_REFRESH, refreshValidityMs);
    }

    private String create(Long userId, String type, long validityMs) {
        Date now = new Date();
        return Jwts.builder()
                // jti: 같은 초에 같은 사용자로 발급해도 토큰이 유일하도록 → refresh rotation 이 의미를 가짐
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId)) //토큰 주인
                .claim(TYPE_CLAIM, type) // "type", type 커스텀 데이터(type) => access, refresh
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validityMs))
                .signWith(key) // 토큰 암호화 키
                .compact();
    }

    /** 서명·만료가 유효한지. 파싱 실패(변조/만료) 시 false. */
    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        return TYPE_ACCESS.equals(typeOf(token));
    }
    //access 토큰에 access 토큰이 들어갔는지 확인하는 로직. refresh 동일
    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(typeOf(token));
    }

    public Long getUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    private String typeOf(String token) {
        return parse(token).get(TYPE_CLAIM, String.class);
    }

    private Claims parse(String token) {
        return Jwts.parser()//parser는 파싱(객체 형태로 만든다) 도구
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

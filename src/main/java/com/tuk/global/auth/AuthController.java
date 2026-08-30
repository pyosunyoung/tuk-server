package com.tuk.global.auth;

import com.tuk.global.auth.dto.LoginRequest;
import com.tuk.global.auth.dto.RefreshRequest;
import com.tuk.global.auth.dto.TokenResponse;
import com.tuk.global.auth.social.OAuthProvider;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 소셜 로그인 → 자체 JWT 발급. 인증 불필요. */
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(OAuthProvider.from(request.provider()), request.token());
    }

    /** refresh 토큰으로 새 access+refresh 발급(rotation). 인증 불필요. */
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /** 로그아웃 = 서버 저장 refresh 파기. access 토큰 인증 필요. */
    @PostMapping("/logout")
    public void logout(@AuthenticationPrincipal Long userId) {
        authService.logout(userId);
    }
}

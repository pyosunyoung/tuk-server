package com.tuk.user.controller;

import com.tuk.user.dto.MeResponse;
import com.tuk.user.service.AccountDeletionService;
import com.tuk.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AccountDeletionService accountDeletionService;

    public UserController(UserService userService, AccountDeletionService accountDeletionService) {
        this.userService = userService;
        this.accountDeletionService = accountDeletionService;
    }

    /** 내 정보. 앱이 로그인 직후 1회 받아 들고 있는다. */
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Long userId) {
        return userService.getMe(userId);
    }

    /** 탈퇴. 되돌릴 수 없다 — 확인은 앱이 받는다. 무엇을 지우는지는 AccountDeletionService. */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal Long userId) {
        accountDeletionService.delete(userId);
    }
}

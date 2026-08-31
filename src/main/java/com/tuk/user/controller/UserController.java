package com.tuk.user.controller;

import com.tuk.user.dto.MeResponse;
import com.tuk.user.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 내 정보. 앱이 로그인 직후 1회 받아 들고 있는다. */
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Long userId) {
        return userService.getMe(userId);
    }
}

package com.tuk.notification.controller;

import com.tuk.notification.dto.DeviceTokenRequest;
import com.tuk.notification.dto.RegisterDeviceTokenRequest;
import com.tuk.notification.service.DeviceTokenService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/device-tokens")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    public DeviceTokenController(DeviceTokenService deviceTokenService) {
        this.deviceTokenService = deviceTokenService;
    }

    /** 기기 푸시 토큰 등록(로그인 후/토큰 갱신 시). 멱등. */
    @PostMapping
    public void register(@AuthenticationPrincipal Long userId,
                         @Valid @RequestBody RegisterDeviceTokenRequest request) {
        deviceTokenService.register(userId, request.token(), request.platform());
    }

    /** 기기 푸시 토큰 해제(로그아웃 시). */
    @DeleteMapping
    public void unregister(@AuthenticationPrincipal Long userId,
                           @Valid @RequestBody DeviceTokenRequest request) {
        deviceTokenService.unregister(userId, request.token());
    }
}

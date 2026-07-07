package com.bungae.presence.controller;

import com.bungae.presence.dto.AvailableFriendResponse;
import com.bungae.presence.dto.MyPresenceResponse;
import com.bungae.presence.dto.TurnOnPresenceRequest;
import com.bungae.presence.service.PresenceService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/presence")
public class PresenceController {

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    /** 콜 켜기. */
    @PostMapping
    public MyPresenceResponse turnOn(@AuthenticationPrincipal Long userId,
                                     @Valid @RequestBody TurnOnPresenceRequest request) {
        return presenceService.turnOn(userId, request.activity(), request.durationMinutes() * 60L);
    }

    /** 콜 끄기. */
    @DeleteMapping
    public void turnOff(@AuthenticationPrincipal Long userId) {
        presenceService.turnOff(userId);
    }

    /** 내 콜 상태 조회. */
    @GetMapping("/me")
    public MyPresenceResponse myPresence(@AuthenticationPrincipal Long userId) {
        return presenceService.getMyPresence(userId);
    }

    /** 지금 한가한 친구 목록. */
    @GetMapping("/friends")
    public List<AvailableFriendResponse> availableFriends(@AuthenticationPrincipal Long userId) {
        return presenceService.getAvailableFriends(userId);
    }
}

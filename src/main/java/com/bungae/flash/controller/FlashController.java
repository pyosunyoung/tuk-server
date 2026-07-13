package com.bungae.flash.controller;

import com.bungae.flash.dto.FlashResponse;
import com.bungae.flash.dto.MeetupRoomResponse;
import com.bungae.flash.dto.ReceivedFlashResponse;
import com.bungae.flash.dto.SendFlashRequest;
import com.bungae.flash.dto.SetPlaceRequest;
import com.bungae.flash.service.FlashService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/flashes")
public class FlashController {

    private final FlashService flashService;

    public FlashController(FlashService flashService) {
        this.flashService = flashService;
    }

    /** 번개 발송. */
    @PostMapping
    public FlashResponse send(@AuthenticationPrincipal Long userId,
                              @Valid @RequestBody SendFlashRequest request) {
        return flashService.sendFlash(userId, request);
    }

    /** 내가 받은 활성 번개 목록. */
    @GetMapping("/received")
    public List<ReceivedFlashResponse> received(@AuthenticationPrincipal Long userId) {
        return flashService.getReceivedFlashes(userId);
    }

    /** 번개 합류. */
    @PostMapping("/{flashId}/join")
    public MeetupRoomResponse join(@AuthenticationPrincipal Long userId,
                                   @PathVariable Long flashId) {
        return flashService.join(userId, flashId);
    }

    /** 모임방 조회. */
    @GetMapping("/{flashId}/room")
    public MeetupRoomResponse room(@AuthenticationPrincipal Long userId,
                                   @PathVariable Long flashId) {
        return flashService.getRoom(userId, flashId);
    }

    /** 모임방 장소 설정. */
    @PatchMapping("/{flashId}/room/place")
    public MeetupRoomResponse setPlace(@AuthenticationPrincipal Long userId,
                                       @PathVariable Long flashId,
                                       @Valid @RequestBody SetPlaceRequest request) {
        return flashService.setPlace(userId, flashId, request);
    }
}

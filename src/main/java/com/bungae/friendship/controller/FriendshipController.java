package com.bungae.friendship.controller;

import com.bungae.friendship.dto.FriendRequestResult;
import com.bungae.friendship.dto.FriendResponse;
import com.bungae.friendship.dto.InviteCodeResponse;
import com.bungae.friendship.dto.ReceivedRequestResponse;
import com.bungae.friendship.dto.RedeemInviteRequest;
import com.bungae.friendship.dto.SendFriendRequest;
import com.bungae.friendship.service.FriendshipService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/friendships")
public class FriendshipController {

    private final FriendshipService friendshipService;

    public FriendshipController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    /** 친구 신청. */
    @PostMapping
    public FriendRequestResult send(@AuthenticationPrincipal Long userId,
                                    @Valid @RequestBody SendFriendRequest request) {
        return friendshipService.sendRequest(userId, request.addresseeId());
    }

    /** 받은 친구 신청 수락. */
    @PostMapping("/{friendshipId}/accept")
    public void accept(@AuthenticationPrincipal Long userId,
                       @PathVariable Long friendshipId) {
        friendshipService.accept(userId, friendshipId);
    }

    /** 내가 받은 수락 대기 신청 목록. */
    @GetMapping("/requests")
    public List<ReceivedRequestResponse> receivedRequests(@AuthenticationPrincipal Long userId) {
        return friendshipService.getReceivedRequests(userId);
    }

    /** 내 친구 목록. */
    @GetMapping
    public List<FriendResponse> friends(@AuthenticationPrincipal Long userId) {
        return friendshipService.getFriends(userId);
    }

    /** 내 친구 초대코드 조회(없으면 생성). */
    @GetMapping("/invite-code")
    public InviteCodeResponse inviteCode(@AuthenticationPrincipal Long userId) {
        return friendshipService.getOrCreateInviteCode(userId);
    }

    /** 초대코드로 친구 맺기. */
    @PostMapping("/redeem")
    public FriendRequestResult redeem(@AuthenticationPrincipal Long userId,
                                      @Valid @RequestBody RedeemInviteRequest request) {
        return friendshipService.redeemInviteCode(userId, request.code());
    }
}

package com.tuk.friendship.controller;

import com.tuk.friendship.dto.FriendRequestResult;
import com.tuk.friendship.dto.FriendResponse;
import com.tuk.friendship.dto.InviteCodeResponse;
import com.tuk.friendship.dto.ReceivedRequestResponse;
import com.tuk.friendship.dto.SentRequestResponse;
import com.tuk.friendship.dto.RedeemInviteRequest;
import com.tuk.friendship.dto.SendFriendRequest;
import com.tuk.friendship.service.FriendshipService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    /** 친구 끊기. 양쪽 목록에서 조용히 사라진다(알림 없음). */
    @DeleteMapping("/friends/{friendUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfriend(@AuthenticationPrincipal Long userId,
                         @PathVariable Long friendUserId) {
        friendshipService.unfriend(userId, friendUserId);
    }

    /** 내가 보낸 신청 취소. 받은 신청을 거절하는 API 는 없다(거절 안 보임). */
    @DeleteMapping("/requests/{friendshipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelRequest(@AuthenticationPrincipal Long userId,
                              @PathVariable Long friendshipId) {
        friendshipService.cancelRequest(userId, friendshipId);
    }

    /** 내가 받은 수락 대기 신청 목록. */
    @GetMapping("/requests")
    public List<ReceivedRequestResponse> receivedRequests(@AuthenticationPrincipal Long userId) {
        return friendshipService.getReceivedRequests(userId);
    }

    /** 내가 보낸 신청(아직 수락 전) 목록. */
    @GetMapping("/requests/sent")
    public List<SentRequestResponse> sentRequests(@AuthenticationPrincipal Long userId) {
        return friendshipService.getSentRequests(userId);
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

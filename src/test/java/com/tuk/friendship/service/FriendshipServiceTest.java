package com.tuk.friendship.service;

import com.tuk.friendship.domain.Friendship;
import com.tuk.friendship.dto.FriendRequestResult;
import com.tuk.friendship.event.FriendshipEvent;
import com.tuk.friendship.dto.InviteCodeResponse;
import com.tuk.friendship.repository.FriendshipRepository;
import com.tuk.global.exception.BadRequestException;
import com.tuk.global.exception.ForbiddenException;
import com.tuk.global.exception.NotFoundException;
import com.tuk.global.exception.TooManyRequestsException;
import com.tuk.user.domain.User;
import com.tuk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

    @Mock FriendshipRepository friendshipRepository;
    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock InviteCodeAttemptLimiter attemptLimiter;
    @InjectMocks FriendshipService friendshipService;

    @Test
    @DisplayName("sendRequest: 자기 자신에게 신청하면 400")
    void sendToSelf() {
        assertThatThrownBy(() -> friendshipService.sendRequest(1L, 1L))
                .isInstanceOf(BadRequestException.class);
    }
    //given 모카 데이터 세팅
    @Test
    @DisplayName("sendRequest: 존재하지 않는 사용자면 404")
    void sendToUnknownUser() {
        given(userRepository.findById(2L)).willReturn(Optional.empty()); // 2번 유저를 호출 시 Optional.empty 데이터가 없다로 설정.

        assertThatThrownBy(() -> friendshipService.sendRequest(1L, 2L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("sendRequest: 관계 없으면 새 PENDING 생성")
    void sendCreatesPending() {
        User addressee = user(2L);
        given(userRepository.findById(2L)).willReturn(Optional.of(addressee));
        given(friendshipRepository.findBetween(1L, 2L)).willReturn(Optional.empty());
        given(userRepository.getReferenceById(1L)).willReturn(user(1L));
        given(friendshipRepository.saveAndFlush(any(Friendship.class)))
                .willAnswer(inv -> withId(inv.getArgument(0), 100L));

        FriendRequestResult result = friendshipService.sendRequest(1L, 2L);

        assertThat(result.friendshipId()).isEqualTo(100L);
        assertThat(result.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("sendRequest: 동시 신청 경합에서 uq 위반(DataIntegrityViolation)이면 400 으로 번역")
    void sendRaceLoserBecomes400() {
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L)));
        given(friendshipRepository.findBetween(1L, 2L)).willReturn(Optional.empty());
        given(userRepository.getReferenceById(1L)).willReturn(user(1L));
        given(friendshipRepository.saveAndFlush(any(Friendship.class)))
                .willThrow(new DataIntegrityViolationException("uq_friend_pair"));

        assertThatThrownBy(() -> friendshipService.sendRequest(1L, 2L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("sendRequest: 상대가 이미 나에게 신청했으면 맞신청으로 즉시 수락(ACCEPTED), 새로 저장 안 함")
    void sendAutoAcceptsReversePending() {
        // 기존: 2 -> 1 (내가 수신자) PENDING
        Friendship reverse = Friendship.builder().requester(user(2L)).addressee(user(1L)).build();
        withId(reverse, 55L);
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L)));
        given(friendshipRepository.findBetween(1L, 2L)).willReturn(Optional.of(reverse));

        FriendRequestResult result = friendshipService.sendRequest(1L, 2L);

        assertThat(result.status()).isEqualTo("ACCEPTED");
        assertThat(reverse.getStatus()).isEqualTo(Friendship.FriendshipStatus.ACCEPTED);
        verify(friendshipRepository, never()).saveAndFlush(any(Friendship.class));
    }

    @Test
    @DisplayName("sendRequest: 이미 친구면 400")
    void sendWhenAlreadyFriends() {
        Friendship accepted = Friendship.builder().requester(user(1L)).addressee(user(2L)).build();
        accepted.accept();
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L)));
        given(friendshipRepository.findBetween(1L, 2L)).willReturn(Optional.of(accepted));

        assertThatThrownBy(() -> friendshipService.sendRequest(1L, 2L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("sendRequest: 내가 이미 신청한 상태면 400(중복)")
    void sendWhenAlreadyRequested() {
        // 기존: 1 -> 2 (내가 신청자) PENDING
        Friendship mine = Friendship.builder().requester(user(1L)).addressee(user(2L)).build();
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L)));
        given(friendshipRepository.findBetween(1L, 2L)).willReturn(Optional.of(mine));

        assertThatThrownBy(() -> friendshipService.sendRequest(1L, 2L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("accept: 수신자가 아니면 403")
    void acceptByNonAddressee() {
        Friendship f = Friendship.builder().requester(user(1L)).addressee(user(2L)).build();
        given(friendshipRepository.findById(55L)).willReturn(Optional.of(f));

        // 나(99)는 수신자(2)가 아님
        assertThatThrownBy(() -> friendshipService.accept(99L, 55L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("accept: 수신자 본인이면 ACCEPTED 로 전환")
    void acceptByAddressee() {
        Friendship f = Friendship.builder().requester(user(1L)).addressee(user(2L)).build();
        given(friendshipRepository.findById(55L)).willReturn(Optional.of(f));

        friendshipService.accept(2L, 55L);

        assertThat(f.getStatus()).isEqualTo(Friendship.FriendshipStatus.ACCEPTED);
    }

    @Test
    @DisplayName("accept: 없는 신청이면 404")
    void acceptUnknown() {
        given(friendshipRepository.findById(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> friendshipService.accept(1L, 404L))
                .isInstanceOf(NotFoundException.class);
    }

    // ── 초대코드 ─────────────────────────────────────────────

    @Test
    @DisplayName("getOrCreateInviteCode: 코드 없으면 8자(혼동문자 제외)로 생성해 반환")
    void inviteCodeGeneratedWhenAbsent() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
        given(userRepository.existsByInviteCode(anyString())).willReturn(false);

        InviteCodeResponse res = friendshipService.getOrCreateInviteCode(1L);

        assertThat(res.code()).matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}");
    }

    @Test
    @DisplayName("getOrCreateInviteCode: 이미 코드가 있으면 그대로 반환(재생성 안 함)")
    void inviteCodeIdempotent() {
        User u = user(1L);
        u.assignInviteCode("AB3K9XQ2");
        given(userRepository.findById(1L)).willReturn(Optional.of(u));

        InviteCodeResponse res = friendshipService.getOrCreateInviteCode(1L);

        assertThat(res.code()).isEqualTo("AB3K9XQ2");
        verify(userRepository, never()).existsByInviteCode(anyString());
    }

    @Test
    @DisplayName("getOrCreateInviteCode: 존재하지 않는 사용자면 404")
    void inviteCodeUnknownUser() {
        given(userRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> friendshipService.getOrCreateInviteCode(9L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("redeemInviteCode: 빈/공백 코드면 400")
    void redeemBlankCode() {
        assertThatThrownBy(() -> friendshipService.redeemInviteCode(1L, "   "))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("redeemInviteCode: 없는 코드면 404 + 실패 횟수를 센다")
    void redeemUnknownCode() {
        given(userRepository.findByInviteCode("ZZZZZZZZ")).willReturn(Optional.empty());

        assertThatThrownBy(() -> friendshipService.redeemInviteCode(1L, "zzzzzzzz"))
                .isInstanceOf(NotFoundException.class);
        verify(attemptLimiter).recordFailure(1L);
    }

    @Test
    @DisplayName("redeemInviteCode: 이미 너무 많이 틀렸으면 코드를 조회하지도 않고 429")
    void redeemBlockedAfterTooManyFailures() {
        willThrow(new TooManyRequestsException("잠시 후")).given(attemptLimiter).checkAllowed(1L);

        assertThatThrownBy(() -> friendshipService.redeemInviteCode(1L, "ab3k9xq2"))
                .isInstanceOf(TooManyRequestsException.class);
        verify(userRepository, never()).findByInviteCode(anyString());
    }

    @Test
    @DisplayName("redeemInviteCode: 본인 코드면 400")
    void redeemOwnCode() {
        given(userRepository.findByInviteCode("MYCODE12")).willReturn(Optional.of(user(1L)));

        assertThatThrownBy(() -> friendshipService.redeemInviteCode(1L, "mycode12"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("redeemInviteCode: 정상 코드면 코드 주인에게 친구신청(PENDING)")
    void redeemCreatesRequest() {
        // 코드 주인 = 2번 유저
        given(userRepository.findByInviteCode("AB3K9XQ2")).willReturn(Optional.of(user(2L)));
        // 이후 sendRequest(1 -> 2) 경로
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L)));
        given(friendshipRepository.findBetween(1L, 2L)).willReturn(Optional.empty());
        given(userRepository.getReferenceById(1L)).willReturn(user(1L));
        given(friendshipRepository.saveAndFlush(any(Friendship.class)))
                .willAnswer(inv -> withId(inv.getArgument(0), 101L));

        FriendRequestResult result = friendshipService.redeemInviteCode(1L, "ab3k9xq2");

        assertThat(result.friendshipId()).isEqualTo(101L);
        assertThat(result.status()).isEqualTo("PENDING");
    }

    // --- 알림 (누구에게 어떤 종류로 가는가) ---

    @Test
    @DisplayName("알림: 새 신청이면 받는 사람에게 FRIEND_REQUEST")
    void notifiesAddresseeOnNewRequest() {
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
        given(friendshipRepository.findBetween(1L, 2L)).willReturn(Optional.empty());
        given(userRepository.getReferenceById(1L)).willReturn(user(1L));
        given(friendshipRepository.saveAndFlush(any(Friendship.class)))
                .willAnswer(inv -> withId(inv.getArgument(0), 77L));

        friendshipService.sendRequest(1L, 2L);

        ArgumentCaptor<FriendshipEvent> captor = ArgumentCaptor.forClass(FriendshipEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(FriendshipEvent.Type.FRIEND_REQUEST);
        assertThat(captor.getValue().targetUserId()).isEqualTo(2L);   // 받는 사람
        assertThat(captor.getValue().actorNickname()).isEqualTo("u1"); // 신청한 사람
    }

    @Test
    @DisplayName("알림: 수락하면 신청했던 사람에게 FRIEND_ACCEPTED")
    void notifiesRequesterOnAccept() {
        Friendship pending = Friendship.builder().requester(user(2L)).addressee(user(1L)).build();
        withId(pending, 55L);
        given(friendshipRepository.findById(55L)).willReturn(Optional.of(pending));

        friendshipService.accept(1L, 55L);

        ArgumentCaptor<FriendshipEvent> captor = ArgumentCaptor.forClass(FriendshipEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(FriendshipEvent.Type.FRIEND_ACCEPTED);
        assertThat(captor.getValue().targetUserId()).isEqualTo(2L);   // 신청했던 사람
        assertThat(captor.getValue().actorNickname()).isEqualTo("u1"); // 수락한 사람
    }

    @Test
    @DisplayName("알림: 맞신청으로 즉시 친구가 되면 '신청 왔어요'가 아니라 '친구가 됐어요'")
    void notifiesAcceptedOnMutualRequest() {
        // 2 -> 1 이 이미 PENDING 인데 1 이 2 에게 신청 → 즉시 ACCEPTED
        Friendship reverse = Friendship.builder().requester(user(2L)).addressee(user(1L)).build();
        withId(reverse, 55L);
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
        given(friendshipRepository.findBetween(1L, 2L)).willReturn(Optional.of(reverse));

        friendshipService.sendRequest(1L, 2L);

        ArgumentCaptor<FriendshipEvent> captor = ArgumentCaptor.forClass(FriendshipEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        // 먼저 신청했던 2 번에게, "친구가 됐어요" 로
        assertThat(captor.getValue().type()).isEqualTo(FriendshipEvent.Type.FRIEND_ACCEPTED);
        assertThat(captor.getValue().targetUserId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("알림: 실패한 신청(이미 친구)은 아무 알림도 내보내지 않는다")
    void noEventWhenRequestFails() {
        Friendship accepted = Friendship.builder().requester(user(1L)).addressee(user(2L)).build();
        accepted.accept();
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L)));
        given(friendshipRepository.findBetween(1L, 2L)).willReturn(Optional.of(accepted));

        assertThatThrownBy(() -> friendshipService.sendRequest(1L, 2L))
                .isInstanceOf(BadRequestException.class);

        verify(eventPublisher, never()).publishEvent(any(FriendshipEvent.class));
    }

    // --- 친구 끊기 / 보낸 신청 취소 ---

    @Test
    @DisplayName("unfriend: 친구면 관계를 지우고, 알림은 보내지 않는다")
    void unfriendDeletesSilently() {
        Friendship accepted = Friendship.builder().requester(user(2L)).addressee(user(1L)).build();
        accepted.accept();
        given(friendshipRepository.findBetween(1L, 2L)).willReturn(Optional.of(accepted));

        friendshipService.unfriend(1L, 2L); // 방향 무관 — 상대가 먼저 신청했던 관계도 끊을 수 있다

        verify(friendshipRepository).delete(accepted);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("unfriend: 아직 신청 단계(PENDING)거나 관계가 없으면 404 — 신청 취소와 섞지 않는다")
    void unfriendRequiresAccepted() {
        Friendship pending = Friendship.builder().requester(user(1L)).addressee(user(2L)).build();
        given(friendshipRepository.findBetween(1L, 2L)).willReturn(Optional.of(pending));
        given(friendshipRepository.findBetween(1L, 3L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> friendshipService.unfriend(1L, 2L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> friendshipService.unfriend(1L, 3L)).isInstanceOf(NotFoundException.class);
        verify(friendshipRepository, never()).delete(any());
    }

    @Test
    @DisplayName("cancelRequest: 내가 보낸 PENDING 신청은 취소된다")
    void cancelOwnPendingRequest() {
        Friendship mine = withId(Friendship.builder().requester(user(1L)).addressee(user(2L)).build(), 50L);
        given(friendshipRepository.findById(50L)).willReturn(Optional.of(mine));

        friendshipService.cancelRequest(1L, 50L);

        verify(friendshipRepository).delete(mine);
    }

    @Test
    @DisplayName("cancelRequest: 받은 신청은 지울 수 없다(거절이 드러나므로), 이미 친구면 400")
    void cannotRejectOrCancelAccepted() {
        Friendship received = withId(Friendship.builder().requester(user(2L)).addressee(user(1L)).build(), 51L);
        given(friendshipRepository.findById(51L)).willReturn(Optional.of(received));
        Friendship accepted = withId(Friendship.builder().requester(user(1L)).addressee(user(3L)).build(), 52L);
        accepted.accept();
        given(friendshipRepository.findById(52L)).willReturn(Optional.of(accepted));

        assertThatThrownBy(() -> friendshipService.cancelRequest(1L, 51L)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> friendshipService.cancelRequest(1L, 52L)).isInstanceOf(BadRequestException.class);
        verify(friendshipRepository, never()).delete(any());
    }

    private static User user(Long id) {
        User u = User.builder().nickname("u" + id).provider("kakao").providerId("pid" + id).build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static Friendship withId(Friendship friendship, Long id) {
        ReflectionTestUtils.setField(friendship, "id", id);
        return friendship;
    }
}

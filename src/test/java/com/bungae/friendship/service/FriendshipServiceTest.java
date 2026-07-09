package com.bungae.friendship.service;

import com.bungae.friendship.domain.Friendship;
import com.bungae.friendship.dto.FriendRequestResult;
import com.bungae.friendship.repository.FriendshipRepository;
import com.bungae.global.exception.BadRequestException;
import com.bungae.global.exception.ForbiddenException;
import com.bungae.global.exception.NotFoundException;
import com.bungae.user.domain.User;
import com.bungae.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

    @Mock FriendshipRepository friendshipRepository;
    @Mock UserRepository userRepository;
    @InjectMocks FriendshipService friendshipService;

    @Test
    @DisplayName("sendRequest: 자기 자신에게 신청하면 400")
    void sendToSelf() {
        assertThatThrownBy(() -> friendshipService.sendRequest(1L, 1L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("sendRequest: 존재하지 않는 사용자면 404")
    void sendToUnknownUser() {
        given(userRepository.findById(2L)).willReturn(Optional.empty());

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

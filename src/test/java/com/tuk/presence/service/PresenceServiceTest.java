package com.tuk.presence.service;

import com.tuk.friendship.repository.FriendshipRepository;
import com.tuk.global.common.Activity;
import com.tuk.presence.domain.Presence;
import com.tuk.presence.dto.AvailableFriendResponse;
import com.tuk.presence.dto.MyPresenceResponse;
import com.tuk.presence.repository.PresenceRepository;
import com.tuk.presence.ws.PresenceEventBroadcaster;
import com.tuk.user.domain.User;
import com.tuk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    @Mock PresenceRepository presenceRepository;
    @Mock FriendshipRepository friendshipRepository;
    @Mock UserRepository userRepository;
    @Mock PresenceEventBroadcaster broadcaster;
    @InjectMocks PresenceService presenceService;

    @Test
    @DisplayName("getAvailableFriends: 절친 중 presence 가 살아있는 사람만, 활동/유저정보 결합")
    void availableFriendsOnlyPresentFriends() {
        given(friendshipRepository.findAcceptedFriendIds(1L)).willReturn(List.of(2L, 3L, 4L));
        // 절친 3명 중 2,4 만 presence 살아있음 (3은 콜 안 켬 → Redis 에 없음)
        given(presenceRepository.findAllById(List.of(2L, 3L, 4L)))
                .willReturn(List.of(presence(2L, Activity.MEAL, 3600L), presence(4L, Activity.CAFE, 1800L)));
        given(userRepository.findAllById(anyIterable()))
                .willReturn(List.of(user(2L, "철수"), user(4L, "영희")));

        List<AvailableFriendResponse> result = presenceService.getAvailableFriends(1L);

        assertThat(result)
                .extracting(AvailableFriendResponse::userId, AvailableFriendResponse::activity)
                .containsExactlyInAnyOrder(tuple(2L, Activity.MEAL), tuple(4L, Activity.CAFE));
        assertThat(result).extracting(AvailableFriendResponse::userId).doesNotContain(3L);
    }

    @Test
    @DisplayName("getAvailableFriends: 친구가 없으면 Redis 조회조차 안 하고 빈 목록")
    void availableFriendsNoFriends() {
        given(friendshipRepository.findAcceptedFriendIds(1L)).willReturn(List.of());

        assertThat(presenceService.getAvailableFriends(1L)).isEmpty();
        verify(presenceRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("getAvailableFriends: 친구는 있지만 아무도 콜을 안 켰으면 빈 목록")
    void availableFriendsNonePresent() {
        given(friendshipRepository.findAcceptedFriendIds(1L)).willReturn(List.of(2L, 3L));
        given(presenceRepository.findAllById(List.of(2L, 3L))).willReturn(List.of());

        assertThat(presenceService.getAvailableFriends(1L)).isEmpty();
        verify(userRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("turnOn: userId·activity·ttl 로 presence 저장하고 상태 반환")
    void turnOn() {
        given(presenceRepository.save(any(Presence.class))).willAnswer(inv -> inv.getArgument(0));

        MyPresenceResponse res = presenceService.turnOn(1L, Activity.DRINK, 7200L);

        ArgumentCaptor<Presence> captor = ArgumentCaptor.forClass(Presence.class);
        verify(presenceRepository).save(captor.capture());
        Presence saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getActivity()).isEqualTo(Activity.DRINK);
        assertThat(saved.getTtlSeconds()).isEqualTo(7200L);

        assertThat(res.available()).isTrue();
        assertThat(res.activity()).isEqualTo(Activity.DRINK);
        assertThat(res.remainingSeconds()).isEqualTo(7200L);

        // 접속 중인 절친에게 실시간 전파
        verify(broadcaster).broadcastAvailable(1L, Activity.DRINK, 7200L);
    }

    @Test
    @DisplayName("turnOff: presence 삭제 + 절친에게 UNAVAILABLE 전파")
    void turnOff() {
        presenceService.turnOff(1L);
        verify(presenceRepository).deleteById(1L);
        verify(broadcaster).broadcastUnavailable(1L);
    }

    @Test
    @DisplayName("getMyPresence: 켜져 있으면 available=true, 없으면 false")
    void getMyPresence() {
        given(presenceRepository.findById(1L)).willReturn(Optional.of(presence(1L, Activity.WALK, 600L)));
        assertThat(presenceService.getMyPresence(1L).available()).isTrue();

        given(presenceRepository.findById(9L)).willReturn(Optional.empty());
        MyPresenceResponse off = presenceService.getMyPresence(9L);
        assertThat(off.available()).isFalse();
        assertThat(off.activity()).isNull();
    }

    private static Presence presence(Long userId, Activity activity, Long ttl) {
        return Presence.builder().userId(userId).activity(activity).ttlSeconds(ttl).build();
    }

    private static User user(Long id, String nickname) {
        User u = User.builder().nickname(nickname).provider("kakao").providerId("pid" + id).build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }
}

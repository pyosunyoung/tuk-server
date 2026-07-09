package com.bungae.presence.ws;

import com.bungae.friendship.repository.FriendshipRepository;
import com.bungae.global.common.Activity;
import com.bungae.presence.dto.PresenceEvent;
import com.bungae.presence.dto.PresenceEventType;
import com.bungae.user.domain.User;
import com.bungae.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PresenceEventBroadcasterTest {

    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock WebSocketSessionRegistry sessionRegistry;
    @Mock FriendshipRepository friendshipRepository;
    @Mock UserRepository userRepository;
    @InjectMocks PresenceEventBroadcaster broadcaster;

    @Test
    @DisplayName("broadcastAvailable: 접속 중인 절친에게만 전송, 오프라인 절친은 건너뜀")
    void broadcastAvailableOnlyOnlineFriends() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user(1L, "나")));
        given(friendshipRepository.findAcceptedFriendIds(1L)).willReturn(List.of(2L, 3L, 4L));
        given(sessionRegistry.isOnline(2L)).willReturn(true);
        given(sessionRegistry.isOnline(3L)).willReturn(false);
        given(sessionRegistry.isOnline(4L)).willReturn(true);

        broadcaster.broadcastAvailable(1L, Activity.MEAL, 3600L);

        // 온라인 친구 2,4 에게만
        verify(messagingTemplate).convertAndSendToUser(eq("2"), eq("/queue/presence"), any(PresenceEvent.class));
        verify(messagingTemplate).convertAndSendToUser(eq("4"), eq("/queue/presence"), any(PresenceEvent.class));
        verify(messagingTemplate, never()).convertAndSendToUser(eq("3"), any(), any(PresenceEvent.class));
    }

    @Test
    @DisplayName("broadcastUnavailable: UNAVAILABLE 이벤트를 접속 중 절친에게 전송")
    void broadcastUnavailable() {
        given(friendshipRepository.findAcceptedFriendIds(1L)).willReturn(List.of(2L));
        given(sessionRegistry.isOnline(2L)).willReturn(true);

        broadcaster.broadcastUnavailable(1L);

        verify(messagingTemplate).convertAndSendToUser(
                eq("2"),
                eq("/queue/presence"),
                argThat((PresenceEvent e) -> e.type() == PresenceEventType.UNAVAILABLE && e.userId().equals(1L)));
    }

    @Test
    @DisplayName("broadcastAvailable: 사용자가 없으면 아무것도 안 보냄")
    void broadcastAvailableUnknownUser() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        broadcaster.broadcastAvailable(1L, Activity.MEAL, 3600L);

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(Object.class));
    }

    private static User user(Long id, String nickname) {
        User u = User.builder().nickname(nickname).provider("kakao").providerId("pid" + id).build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }
}

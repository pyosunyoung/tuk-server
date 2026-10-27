package com.tuk.friendship.ws;

import com.tuk.friendship.event.FriendsChangedEvent;
import com.tuk.presence.ws.WebSocketSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FriendsChangedBroadcasterTest {

    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock WebSocketSessionRegistry sessionRegistry;
    @InjectMocks FriendsChangedBroadcaster broadcaster;

    @Test
    @DisplayName("상대가 접속 중이면 /queue/friends 로 목록 갱신 신호만 보낸다")
    void sendsSignalWhenOnline() {
        given(sessionRegistry.isOnline(2L)).willReturn(true);

        broadcaster.onFriendsChanged(new FriendsChangedEvent(2L));

        verify(messagingTemplate).convertAndSendToUser("2", "/queue/friends", FriendsChangedBroadcaster.PAYLOAD);
    }

    @Test
    @DisplayName("접속 중이 아니면 아무것도 보내지 않는다(다음에 앱을 켤 때 새로 불러온다 · 푸시 없음)")
    void skipsWhenOffline() {
        given(sessionRegistry.isOnline(2L)).willReturn(false);

        broadcaster.onFriendsChanged(new FriendsChangedEvent(2L));

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("전송이 실패해도 예외를 밖으로 던지지 않는다(친구 끊기 요청 자체는 성공)")
    void isolatesFailure() {
        given(sessionRegistry.isOnline(2L)).willReturn(true);
        willThrow(new RuntimeException("boom"))
                .given(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());

        assertThatCode(() -> broadcaster.onFriendsChanged(new FriendsChangedEvent(2L)))
                .doesNotThrowAnyException();
    }
}

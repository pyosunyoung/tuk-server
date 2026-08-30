package com.tuk.flash.ws;

import com.tuk.flash.dto.FlashPushEvent;
import com.tuk.global.common.Activity;
import com.tuk.presence.ws.WebSocketSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FlashEventBroadcasterTest {

    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock WebSocketSessionRegistry sessionRegistry;
    @InjectMocks FlashEventBroadcaster broadcaster;

    @Test
    @DisplayName("onFlashBroadcast: 접속 중인 대상에게만 /queue/flash 로 전송")
    void sendsOnlyToOnline() {
        FlashPushEvent payload = FlashPushEvent.newFlash(
                10L, 1L, "보낸이", null, Activity.MEAL, "밥먹자",
                null, LocalDateTime.now().plusHours(1));
        given(sessionRegistry.isOnline(2L)).willReturn(true);
        given(sessionRegistry.isOnline(3L)).willReturn(false);

        broadcaster.onFlashBroadcast(new FlashBroadcastEvent(payload, List.of(2L, 3L)));

        verify(messagingTemplate).convertAndSendToUser(eq("2"), eq("/queue/flash"), any(FlashPushEvent.class));
        verify(messagingTemplate, never()).convertAndSendToUser(eq("3"), any(), any(FlashPushEvent.class));
    }

    @Test
    @DisplayName("onFlashBroadcast: 한 대상 전송이 실패해도 다른 대상 전송은 계속(격리)")
    void isolatesFailure() {
        FlashPushEvent payload = FlashPushEvent.memberJoined(10L, 2L, "합류자", null);
        given(sessionRegistry.isOnline(3L)).willReturn(true);
        given(sessionRegistry.isOnline(4L)).willReturn(true);
        // 3번 전송에서 예외
        org.mockito.BDDMockito.willThrow(new RuntimeException("boom"))
                .given(messagingTemplate).convertAndSendToUser(eq("3"), any(), any(FlashPushEvent.class));

        broadcaster.onFlashBroadcast(new FlashBroadcastEvent(payload, List.of(3L, 4L)));

        // 4번은 여전히 전송됨
        verify(messagingTemplate).convertAndSendToUser(eq("4"), eq("/queue/flash"), any(FlashPushEvent.class));
    }
}

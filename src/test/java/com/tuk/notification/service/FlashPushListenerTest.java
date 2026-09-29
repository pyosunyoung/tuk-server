package com.tuk.notification.service;

import com.tuk.flash.dto.FlashPushEvent;
import com.tuk.flash.ws.FlashBroadcastEvent;
import com.tuk.global.common.Activity;
import com.tuk.notification.dto.PushMessage;
import com.tuk.presence.ws.WebSocketSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FlashPushListenerTest {

    @Mock WebSocketSessionRegistry sessionRegistry;
    @Mock PushNotificationService pushNotificationService;
    @InjectMocks FlashPushListener listener;

    @Test
    @DisplayName("오프라인 대상에게만 푸시(온라인은 WebSocket 이 담당)")
    void pushesOnlyOfflineTargets() {
        given(sessionRegistry.isOnline(2L)).willReturn(true);   // 온라인 → WS
        given(sessionRegistry.isOnline(3L)).willReturn(false);  // 오프라인 → FCM
        given(sessionRegistry.isOnline(4L)).willReturn(false);

        listener.onFlashBroadcast(new FlashBroadcastEvent(newFlashPayload(), List.of(2L, 3L, 4L)));

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(pushNotificationService).pushToUsers(captor.capture(), any(PushMessage.class));
        assertThat(captor.getValue()).containsExactly(3L, 4L);
    }

    @Test
    @DisplayName("전원 접속 중이면 푸시하지 않는다")
    void noPushWhenAllOnline() {
        given(sessionRegistry.isOnline(2L)).willReturn(true);

        listener.onFlashBroadcast(new FlashBroadcastEvent(newFlashPayload(), List.of(2L)));

        verify(pushNotificationService, never()).pushToUsers(any(), any());
    }

    @Test
    @DisplayName("NEW_FLASH: 보낸이 닉네임과 메시지로 푸시 본문 구성, data 에 flashId 포함")
    void buildsNewFlashMessage() {
        given(sessionRegistry.isOnline(3L)).willReturn(false);

        listener.onFlashBroadcast(new FlashBroadcastEvent(newFlashPayload(), List.of(3L)));

        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushNotificationService).pushToUsers(any(), captor.capture());
        PushMessage message = captor.getValue();
        assertThat(message.title()).contains("철수");
        assertThat(message.body()).isEqualTo("밥먹자");
        assertThat(message.data()).containsEntry("flashId", "10").containsEntry("type", "NEW_FLASH");
    }

    @Test
    @DisplayName("MEMBER_JOINED: 합류 알림 문구로 구성")
    void buildsMemberJoinedMessage() {
        given(sessionRegistry.isOnline(1L)).willReturn(false);

        listener.onFlashBroadcast(new FlashBroadcastEvent(
                FlashPushEvent.memberJoined(10L, 2L, "영희", null), List.of(1L)));

        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushNotificationService).pushToUsers(any(), captor.capture());
        assertThat(captor.getValue().body()).contains("영희");
        assertThat(captor.getValue().data()).containsEntry("type", "MEMBER_JOINED");
    }

    @Test
    @DisplayName("PLACE_UPDATED: 처음 정함과 바꿈을 다른 제목으로, 바꿈은 이전 장소까지")
    void buildsPlaceMessages() {
        given(sessionRegistry.isOnline(2L)).willReturn(false);

        PushMessage first = pushed(FlashPushEvent.placeUpdated(10L, 1L, "민수", null, null, "홍대입구역"));
        assertThat(first.title()).isEqualTo("장소가 정해졌어요");
        assertThat(first.body()).isEqualTo("홍대입구역 · 민수님이 정했어요");
        assertThat(first.data()).containsEntry("type", "PLACE_UPDATED").containsEntry("flashId", "10");

        PushMessage change = pushed(FlashPushEvent.placeUpdated(10L, 1L, "민수", null, "홍대입구역", "합정역"));
        assertThat(change.title()).isEqualTo("장소가 바뀌었어요");
        assertThat(change.body()).isEqualTo("홍대입구역 → 합정역 · 민수님이 바꿨어요");
    }

    @Test
    @DisplayName("MEET_AT_UPDATED: 처음 정함과 바꿈을 다른 제목으로, 바꿈은 이전 시간까지")
    void buildsMeetAtMessages() {
        given(sessionRegistry.isOnline(2L)).willReturn(false);
        LocalDateTime base = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0);

        PushMessage first = pushed(FlashPushEvent.meetAtUpdated(10L, 1L, "민수", null, null, base));
        assertThat(first.title()).isEqualTo("약속 시간이 정해졌어요");
        assertThat(first.body()).endsWith("· 민수님이 정했어요");

        PushMessage change = pushed(FlashPushEvent.meetAtUpdated(10L, 1L, "민수", null, base, base.plusMinutes(30)));
        assertThat(change.title()).isEqualTo("약속 시간이 바뀌었어요");
        assertThat(change.body()).contains(" → ").endsWith("· 민수님이 바꿨어요");
        assertThat(change.data()).containsEntry("type", "MEET_AT_UPDATED");
    }

    private PushMessage pushed(FlashPushEvent payload) {
        org.mockito.Mockito.clearInvocations(pushNotificationService);
        listener.onFlashBroadcast(new FlashBroadcastEvent(payload, List.of(2L)));
        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushNotificationService).pushToUsers(any(), captor.capture());
        return captor.getValue();
    }

    private static FlashPushEvent newFlashPayload() {
        return FlashPushEvent.newFlash(10L, 1L, "철수", null, Activity.MEAL, "밥먹자",
                null, LocalDateTime.now().plusHours(1));
    }
}

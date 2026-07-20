package com.bungae.notification.service;

import com.bungae.flash.dto.FlashPushEvent;
import com.bungae.flash.ws.FlashBroadcastEvent;
import com.bungae.notification.dto.PushMessage;
import com.bungae.presence.ws.WebSocketSessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

/**
 * 번개 이벤트의 오프라인 대상에게 FCM 푸시.
 *
 * 같은 FlashBroadcastEvent 를 FlashEventBroadcaster(접속자 → WebSocket)와 나눠 처리한다:
 *  - 접속 중  → WebSocket (FlashEventBroadcaster)
 *  - 미접속   → FCM       (여기)
 * 둘 다 AFTER_COMMIT 이라 롤백 시에는 아무것도 나가지 않는다.
 *
 * presence(콜 켜기/끄기)는 의도적으로 푸시하지 않는다 — 앱을 열면 보이는 주변 상태라 푸시하면 시끄럽다.
 */
@Component
public class FlashPushListener {

    private final WebSocketSessionRegistry sessionRegistry;
    private final PushNotificationService pushNotificationService;

    public FlashPushListener(WebSocketSessionRegistry sessionRegistry,
                             PushNotificationService pushNotificationService) {
        this.sessionRegistry = sessionRegistry;
        this.pushNotificationService = pushNotificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFlashBroadcast(FlashBroadcastEvent event) {
        List<Long> offlineTargets = event.targetUserIds().stream()
                .filter(userId -> !sessionRegistry.isOnline(userId))
                .toList();
        if (offlineTargets.isEmpty()) {
            return;
        }
        pushNotificationService.pushToUsers(offlineTargets, toPushMessage(event.payload()));
    }

    private PushMessage toPushMessage(FlashPushEvent event) {
        Map<String, String> data = Map.of(
                "type", event.type().name(),
                "flashId", String.valueOf(event.flashId()));

        return switch (event.type()) {
            case NEW_FLASH -> new PushMessage(
                    event.nickname() + "님의 번개",
                    hasText(event.message()) ? event.message() : "지금 볼래?",
                    data);
            case MEMBER_JOINED -> new PushMessage(
                    "번개 합류",
                    event.nickname() + "님이 합류했어요",
                    data);
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

package com.tuk.notification.service;

import com.tuk.flash.dto.FlashPushEvent;
import com.tuk.flash.ws.FlashBroadcastEvent;
import com.tuk.notification.dto.PushMessage;
import com.tuk.presence.ws.WebSocketSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
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

    private static final Logger log = LoggerFactory.getLogger(FlashPushListener.class);

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
        log.info("번개 푸시 대상: 전체 {}명 중 오프라인 {}명 (온라인은 WebSocket 으로)",
                event.targetUserIds().size(), offlineTargets.size());
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
            // 처음 정함과 바꿈을 구분한다. 바꿈은 이전 값을 보여줘야 "어, 바뀌었네"가 된다.
            case PLACE_UPDATED -> event.placeBefore() == null
                    ? new PushMessage("장소가 정해졌어요",
                            event.placeAfter() + " · " + event.nickname() + "님이 정했어요", data)
                    : new PushMessage("장소가 바뀌었어요",
                            event.placeBefore() + " → " + event.placeAfter()
                                    + " · " + event.nickname() + "님이 바꿨어요", data);
            case MEET_AT_UPDATED -> {
                LocalDateTime now = LocalDateTime.now();
                yield event.meetAtBefore() == null
                        ? new PushMessage("약속 시간이 정해졌어요",
                                MeetTimeLabel.of(event.meetAtAfter(), now)
                                        + " · " + event.nickname() + "님이 정했어요", data)
                        : new PushMessage("약속 시간이 바뀌었어요",
                                MeetTimeLabel.change(event.meetAtBefore(), event.meetAtAfter(), now)
                                        + " · " + event.nickname() + "님이 바꿨어요", data);
            }
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

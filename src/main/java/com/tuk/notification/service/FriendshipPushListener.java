package com.tuk.notification.service;

import com.tuk.friendship.event.FriendshipEvent;
import com.tuk.notification.dto.PushMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

/**
 * 친구 신청·수락 FCM 푸시. 번개와 같이 AFTER_COMMIT — 롤백 시에는 아무것도 나가지 않는다.
 *
 * 번개는 접속자/미접속자를 나눠(WebSocket/FCM) 보내지만 친구는 그러지 않는다.
 * 급한 알림이 아니라 실시간 화면 갱신이 필요 없고, 앱이 켜져 있어도 배지가 바로
 * 올라가야 해서 한 경로로 보내는 편이 단순하다.
 */
@Component
public class FriendshipPushListener {

    private static final Logger log = LoggerFactory.getLogger(FriendshipPushListener.class);

    private final PushNotificationService pushNotificationService;

    public FriendshipPushListener(PushNotificationService pushNotificationService) {
        this.pushNotificationService = pushNotificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFriendship(FriendshipEvent event) {
        log.info("친구 푸시: type={} target={}", event.type(), event.targetUserId());
        pushNotificationService.pushToUsers(List.of(event.targetUserId()), toPushMessage(event));
    }

    private PushMessage toPushMessage(FriendshipEvent event) {
        Map<String, String> data = Map.of("type", event.type().name());

        return switch (event.type()) {
            case FRIEND_REQUEST -> new PushMessage(
                    "친구 신청",
                    event.actorNickname() + "님이 친구 신청했어요",
                    data);
            case FRIEND_ACCEPTED -> new PushMessage(
                    "친구가 됐어요",
                    event.actorNickname() + "님과 친구가 됐어요",
                    data);
        };
    }
}

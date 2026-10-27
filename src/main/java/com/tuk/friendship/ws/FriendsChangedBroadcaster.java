package com.tuk.friendship.ws;

import com.tuk.friendship.event.FriendsChangedEvent;
import com.tuk.presence.ws.WebSocketSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * 친구 관계가 사라졌을 때 상대가 접속 중이면 /user/queue/friends 로 "목록 다시 불러와" 신호만 보낸다.
 * 화면에 아무것도 띄우지 않는 데이터 신호라 FCM 은 쓰지 않는다 — 앱이 꺼져 있으면 다음에 켤 때 어차피 새로 불러온다.
 * 커밋 뒤에만(AFTER_COMMIT) — 롤백되면 상대 목록도 그대로여야 한다.
 */
@Component
public class FriendsChangedBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(FriendsChangedBroadcaster.class);
    static final String DESTINATION = "/queue/friends";
    static final Map<String, String> PAYLOAD = Map.of("type", "FRIENDS_CHANGED");

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionRegistry sessionRegistry;

    public FriendsChangedBroadcaster(SimpMessagingTemplate messagingTemplate,
                                     WebSocketSessionRegistry sessionRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.sessionRegistry = sessionRegistry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFriendsChanged(FriendsChangedEvent event) {
        Long target = event.targetUserId();
        if (!sessionRegistry.isOnline(target)) {
            return;
        }
        try {
            messagingTemplate.convertAndSendToUser(String.valueOf(target), DESTINATION, PAYLOAD);
        } catch (RuntimeException e) {
            log.warn("친구 목록 변경 신호 실패 (to={})", target, e);
        }
    }
}

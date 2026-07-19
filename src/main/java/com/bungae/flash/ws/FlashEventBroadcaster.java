package com.bungae.flash.ws;

import com.bungae.presence.ws.WebSocketSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 번개 실시간 전파. 트랜잭션 커밋 이후에만 발송해 롤백 시 유령 알림을 막는다(AFTER_COMMIT).
 * 접속 중인 대상에게만 /user/queue/flash 로 전달하며, 전송 실패는 격리한다.
 */
@Component
public class FlashEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(FlashEventBroadcaster.class);
    private static final String DESTINATION = "/queue/flash";

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionRegistry sessionRegistry;

    public FlashEventBroadcaster(SimpMessagingTemplate messagingTemplate,
                                 WebSocketSessionRegistry sessionRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.sessionRegistry = sessionRegistry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFlashBroadcast(FlashBroadcastEvent event) {
        for (Long targetId : event.targetUserIds()) {
            if (sessionRegistry.isOnline(targetId)) {
                try {
                    messagingTemplate.convertAndSendToUser(String.valueOf(targetId), DESTINATION, event.payload());
                } catch (RuntimeException e) {
                    log.warn("flash 전파 실패 (type={}, flashId={}, to={})",
                            event.payload().type(), event.payload().flashId(), targetId, e);
                }
            }
        }
    }
}

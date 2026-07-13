package com.bungae.presence.ws;

import com.bungae.friendship.repository.FriendshipRepository;
import com.bungae.global.common.Activity;
import com.bungae.presence.dto.PresenceEvent;
import com.bungae.user.domain.User;
import com.bungae.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * presence 변화를 "절친 중 접속자"에게만 WebSocket 으로 전파.
 * 각 친구는 /user/queue/presence 를 구독하면 자신에게 온 이벤트를 받는다.
 *
 * 전파는 부수효과(side effect)다 — 한 친구에게 보내다 실패해도 나머지 전파와
 * 이를 호출한 본 작업(콜 켜기/끄기)은 영향받지 않아야 하므로 각 전송을 격리한다.
 */
@Component
public class PresenceEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(PresenceEventBroadcaster.class);
    private static final String DESTINATION = "/queue/presence";

    private final SimpMessagingTemplate messagingTemplate; //스프링이 제공하는 웹소켓 메시지 배달부
    private final WebSocketSessionRegistry sessionRegistry;
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    public PresenceEventBroadcaster(SimpMessagingTemplate messagingTemplate,
                                    WebSocketSessionRegistry sessionRegistry,
                                    FriendshipRepository friendshipRepository,
                                    UserRepository userRepository) {
        this.messagingTemplate = messagingTemplate;
        this.sessionRegistry = sessionRegistry;
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
    }

    /** userId 가 콜을 켰음을 절친들에게 알린다(프로필/활동 포함). */
    public void broadcastAvailable(Long userId, Activity activity, Long remainingSeconds) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        PresenceEvent event = PresenceEvent.available(
                user.getId(), user.getNickname(), user.getProfileImage(), activity, remainingSeconds);
        sendToOnlineFriends(userId, event);
    }

    /** userId 가 콜을 껐음을 절친들에게 알린다. */
    public void broadcastUnavailable(Long userId) {
        sendToOnlineFriends(userId, PresenceEvent.unavailable(userId));
    }

    private void sendToOnlineFriends(Long sourceUserId, PresenceEvent event) {
        for (Long friendId : friendshipRepository.findAcceptedFriendIds(sourceUserId)) {
            if (sessionRegistry.isOnline(friendId)) {
                try {
                    messagingTemplate.convertAndSendToUser(String.valueOf(friendId), DESTINATION, event);
                } catch (RuntimeException e) {
                    // 전파 실패가 콜 켜기/끄기 자체를 실패시키지 않도록 격리
                    log.warn("presence 전파 실패 (from={}, to={})", sourceUserId, friendId, e);
                }
            }
        }
    }
}

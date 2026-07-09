package com.bungae.presence.ws;

import com.bungae.friendship.repository.FriendshipRepository;
import com.bungae.global.common.Activity;
import com.bungae.presence.dto.PresenceEvent;
import com.bungae.user.domain.User;
import com.bungae.user.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * presence 변화를 "절친 중 접속자"에게만 WebSocket 으로 전파.
 * 각 친구는 /user/queue/presence 를 구독하면 자신에게 온 이벤트를 받는다.
 */
@Component
public class PresenceEventBroadcaster {

    private static final String DESTINATION = "/queue/presence";

    private final SimpMessagingTemplate messagingTemplate;
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
                messagingTemplate.convertAndSendToUser(String.valueOf(friendId), DESTINATION, event);
            }
        }
    }
}

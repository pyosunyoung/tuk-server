package com.bungae.presence.ws;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * userId ↔ WS 세션 매핑. 접속/해제 이벤트로 갱신한다.
 * "절친 중 접속자에게만" 실시간 전파할 때 온라인 여부 판단에 쓴다.
 * (멀티 서버로 확장하면 이 레지스트리를 Redis Pub/Sub 기반으로 교체)
 *
 * 한 사용자가 여러 기기로 접속할 수 있으므로 userId → {sessionId} (다중).
 */
@Component
public class WebSocketSessionRegistry {

    private final Map<Long, Set<String>> userToSessions = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionToUser = new ConcurrentHashMap<>();
    //userToSessions: 유저 ID를 넣으면 그 유저가 연결한 모든 세션 리스트를 줍니다. (예: "1번 유저 지금 폰이랑 PC로 접속 중이네?")
    //sessionToUser: 반대로 세션 ID를 넣으면 주인이 누구인지 바로 알려줍니다. (예: "방금 끊긴 abc123xx 세션은 1번 유저 꺼였네?")

    public void register(Long userId, String sessionId) {
        userToSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        sessionToUser.put(sessionId, userId);
    }

    public void unregister(String sessionId) {
        Long userId = sessionToUser.remove(sessionId);
        if (userId == null) {
            return;
        }
        Set<String> sessions = userToSessions.get(userId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                userToSessions.remove(userId);
            }
        }
    }

    public boolean isOnline(Long userId) {
        Set<String> sessions = userToSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    } // 온라인 상태 여부

    public Set<Long> onlineUsers() {
        return Set.copyOf(userToSessions.keySet());
    }

    // --- STOMP 생명주기 이벤트 → 등록/해제 ---

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        // 1. 웹소켓 연결 성공 이벤트가 발생하면 작동!
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        String sessionId = accessor.getSessionId();
        if (user != null && sessionId != null) {
            register(Long.valueOf(user.getName()), sessionId);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        if (event.getSessionId() != null) {
            unregister(event.getSessionId());
        }
    }
}

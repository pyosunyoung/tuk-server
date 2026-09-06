package com.tuk.global.config;

import com.tuk.presence.ws.StompAuthChannelInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket.
 *  - 핸드셰이크 엔드포인트: /ws (SecurityConfig 에서 permitAll, 인증은 CONNECT 에서)
 *  - 브로커: 인메모리 SimpleBroker(/topic, /queue). 앱→서버는 /app, 유저 대상은 /user
 *  - CONNECT 인증: StompAuthChannelInterceptor (인바운드 채널)
 *  - heartbeat: 죽은 연결을 걷어내기 위해 필수 (아래 configureMessageBroker 참고)
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authChannelInterceptor;

    public WebSocketConfig(StompAuthChannelInterceptor authChannelInterceptor) {
        this.authChannelInterceptor = authChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Flutter STOMP 클라이언트는 raw WebSocket → SockJS 미사용. CORS 는 개발용으로 개방.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // heartbeat 를 켜지 않으면 클라이언트가 FIN 없이 사라져도(지하철·비행기모드·강제종료)
        // 세션이 레지스트리에 남아 계속 "온라인"으로 판단된다.
        // 그러면 번개가 죽은 소켓으로만 나가고 FCM 푸시는 건너뛰어, 사용자는 아무것도 못 받는다.
        // {서버가 보내는 주기, 서버가 클라이언트에게 기대하는 주기} — 앱 클라이언트 기본값은 5초.
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(webSocketHeartbeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    /** heartbeat 전송 전용 스케줄러. 없으면 setHeartbeatValue 가 동작하지 않는다. */
    @Bean
    public TaskScheduler webSocketHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}

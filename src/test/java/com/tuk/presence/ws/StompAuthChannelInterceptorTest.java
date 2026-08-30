package com.tuk.presence.ws;

import com.tuk.global.auth.jwt.JwtProperties;
import com.tuk.global.auth.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class StompAuthChannelInterceptorTest {

    private JwtTokenProvider tokenProvider;
    private StompAuthChannelInterceptor interceptor;
    private final MessageChannel channel = mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-that-is-at-least-32-bytes-long-0123456789");
        props.setAccessTokenValidity(Duration.ofMinutes(30));
        props.setRefreshTokenValidity(Duration.ofDays(14));
        tokenProvider = new JwtTokenProvider(props);
        interceptor = new StompAuthChannelInterceptor(tokenProvider);
    }

    @Test
    @DisplayName("CONNECT + 유효 access 토큰 → 세션 Principal(userId) 설정")
    void connectWithValidToken() {
        String token = tokenProvider.createAccessToken(42L);
        Message<?> message = connectMessage("Bearer " + token);

        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor out = StompHeaderAccessor.wrap(result);
        assertThat(out.getUser()).isNotNull();
        assertThat(out.getUser().getName()).isEqualTo("42");
    }

    @Test
    @DisplayName("CONNECT + 토큰 없음 → 거부")
    void connectWithoutToken() {
        Message<?> message = connectMessage(null);
        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("CONNECT + refresh 토큰 → 거부(access 만 허용)")
    void connectWithRefreshToken() {
        Message<?> message = connectMessage("Bearer " + tokenProvider.createRefreshToken(42L));
        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    private Message<byte[]> connectMessage(String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authHeader != null) {
            accessor.setNativeHeader("Authorization", authHeader);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}

package com.tuk.presence.ws;

import com.tuk.global.auth.jwt.JwtTokenProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * STOMP CONNECT 프레임의 Authorization: Bearer &lt;access token&gt; 를 검증하고
 * 세션 Principal(userId)을 설정한다. 이후 그 세션의 메시지는 이 사용자로 인식되고,
 * convertAndSendToUser(userId, ...) 로 해당 세션에 전달된다.
 * 인증 실패 시 CONNECT 를 거부(ERROR 프레임).
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {
    //ChannelInterceptor: 웹소켓 메시지가 서버에 최종 도착하기 전/후에 메시지를 가로채서(Intercept) 무언가 사전 처리를 할 수 있도록 해주는 스프링의 도구입니다.
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public StompAuthChannelInterceptor(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) { //메시지가 실제로 전송되기 직전(pre)에 실행되는 메서드입니다.
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = resolveToken(accessor.getFirstNativeHeader(AUTH_HEADER));
            if (token == null || !tokenProvider.isValid(token) || !tokenProvider.isAccessToken(token)) {
                throw new MessageDeliveryException("WebSocket 인증에 실패했습니다.");
            }
            Long userId = tokenProvider.getUserId(token);

            // 스프링 시큐리티 시스템이 이해할 수 있는 형태의 인증 명패(Token)를 만듭니다.
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            //💡 accessor.setUser(...)의 엄청난 효과
            //이 검증이 끝나고 나면 스프링 웹소켓 세션에 "이 연결의 주인은 userId번 유저다"라고 명패를 붙여두게 됩니다.
            //덕분에 바로 전 질문에서 보셨던 WebSocketSessionRegistry가 SessionConnectedEvent를 받았을 때 accessor.getUser()를 통해 유저 ID를 안전하게 꺼내서 온라인 등록을 처리할 수 있었던 것입니다. 두 코드가 완벽하게 퍼즐처럼 맞물려 있죠!
        }
        return message;
    }
    //"Bearer " 문자를 제거 후 토큰만 추출 메서드
    private String resolveToken(String header) {
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}


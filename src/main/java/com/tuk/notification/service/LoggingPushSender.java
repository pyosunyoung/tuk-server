package com.tuk.notification.service;

import com.tuk.notification.dto.PushMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 기본(개발용) 구현. 실제로 보내지 않고 로그만 남긴다 → Firebase 키 없이도 전체 흐름을 돌려볼 수 있다.
 * fcm.enabled=true 로 켜면 FcmPushSender 가 대신 등록된다.
 */
@Component
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public List<String> send(List<String> tokens, PushMessage message) {
        log.info("[PUSH(dev)] tokens={} title='{}' body='{}' data={}",
                tokens.size(), message.title(), message.body(), message.data());
        return List.of(); // 무효 토큰 없음
    }
}

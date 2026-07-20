package com.bungae.notification.service;

import com.bungae.notification.dto.PushMessage;

import java.util.List;

/**
 * 푸시 전송 추상화. 실제 FCM 연동 없이도 개발·테스트가 가능하도록 인터페이스로 분리한다.
 * (fcm.enabled=false → LoggingPushSender, true → FcmPushSender)
 */
public interface PushSender {

    /**
     * 여러 기기 토큰에 같은 메시지를 보낸다.
     *
     * @return 더 이상 유효하지 않은 토큰들(FCM 의 UNREGISTERED 등). 호출측이 정리한다.
     */
    List<String> send(List<String> tokens, PushMessage message);
}

package com.bungae.notification.service;

import com.bungae.notification.domain.DeviceToken;
import com.bungae.notification.dto.PushMessage;
import com.bungae.notification.repository.DeviceTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
public class PushNotificationService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final PushSender pushSender;

    public PushNotificationService(DeviceTokenRepository deviceTokenRepository, PushSender pushSender) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.pushSender = pushSender;
    }

    /**
     * 여러 사용자의 모든 기기로 푸시. 발송 결과로 돌아온 죽은 토큰(UNREGISTERED)은 정리한다.
     *
     * REQUIRES_NEW: 이 메서드는 원 트랜잭션 커밋 이후(AFTER_COMMIT 리스너)에 호출되므로
     * 별도 트랜잭션으로 실행해야 DB 작업이 안전하게 커밋된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void pushToUsers(Collection<Long> userIds, PushMessage message) {
        if (userIds.isEmpty()) {
            return;
        }
        List<DeviceToken> deviceTokens = deviceTokenRepository.findByUserIdIn(userIds);
        if (deviceTokens.isEmpty()) {
            return;
        }

        List<String> tokens = deviceTokens.stream().map(DeviceToken::getToken).toList();
        List<String> deadTokens = pushSender.send(tokens, message);
        deadTokens.forEach(deviceTokenRepository::deleteByToken);
    }
}

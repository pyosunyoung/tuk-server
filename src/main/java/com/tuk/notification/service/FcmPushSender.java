package com.tuk.notification.service;

import com.tuk.notification.dto.PushMessage;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 실제 FCM 발송. fcm.enabled=true 일 때만 빈으로 등록된다(키가 없으면 아예 생성되지 않음).
 * UNREGISTERED/INVALID_ARGUMENT 토큰은 죽은 토큰이므로 호출측이 지우도록 돌려준다.
 */
@Component
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "true")
public class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);

    private final FirebaseMessaging firebaseMessaging;

    public FcmPushSender(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    @Override
    public List<String> send(List<String> tokens, PushMessage message) {
        MulticastMessage multicast = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(message.data())
                .build();

        try {
            BatchResponse response = firebaseMessaging.sendEachForMulticast(multicast);
            return collectDeadTokens(tokens, response);
        } catch (FirebaseMessagingException e) {
            log.warn("FCM 발송 실패 (tokens={})", tokens.size(), e);
            return List.of();
        }
    }

    private List<String> collectDeadTokens(List<String> tokens, BatchResponse response) {
        List<String> dead = new ArrayList<>();
        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse each = responses.get(i);
            if (each.isSuccessful() || each.getException() == null) {
                continue;
            }
            MessagingErrorCode code = each.getException().getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                dead.add(tokens.get(i));
            }
        }
        return dead;
    }
}

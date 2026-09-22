package com.tuk.notification.service;

import com.tuk.notification.dto.PushMessage;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
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

    /** 앱이 만든 알림 채널 id 와 같아야 한다(앱: PushService._channel, AndroidManifest 기본 채널). */
    private static final String ANDROID_CHANNEL_ID = "tuk_flash";

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
                // 안드로이드 전용 설정이 없으면 '보통' 우선순위로 나간다 →
                // 화면 위에 뜨는 배너(heads-up)가 안 생기고, Doze 중이면 전달도 늦어진다.
                // 번개는 "지금 볼래?" 라 늦게 도착하면 의미가 없으므로 HIGH.
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(ANDROID_CHANNEL_ID)
                                .setPriority(AndroidNotification.Priority.HIGH)
                                .setDefaultSound(true)
                                .setDefaultVibrateTimings(true)
                                .build())
                        .build())
                // iOS 도 같은 이유로 전용 설정이 필요하다. 없으면 무음으로 오고,
                // apns-priority 기본값(5)은 배터리를 아끼려 묶어 보내서 도착이 늦어진다.
                // 제목·본문은 위 Notification 에서 그대로 내려온다(Aps 에는 소리만 지정).
                .setApnsConfig(ApnsConfig.builder()
                        .putHeader("apns-priority", "10")
                        .setAps(Aps.builder()
                                .setSound("default")
                                .build())
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
            // 죽은 토큰이 아닌 실패(설정 오류·권한·쿼터 등)는 조용히 넘기면 원인을 영영 못 찾는다.
            log.warn("FCM 발송 실패 code={} msg={}", code, each.getException().getMessage());
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                dead.add(tokens.get(i));
            }
        }
        if (dead.isEmpty() && response.getFailureCount() == 0) {
            log.info("FCM 발송 성공 count={}", response.getSuccessCount());
        }
        return dead;
    }
}

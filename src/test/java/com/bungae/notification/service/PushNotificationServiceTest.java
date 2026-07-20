package com.bungae.notification.service;

import com.bungae.notification.domain.DeviceToken;
import com.bungae.notification.domain.DeviceToken.Platform;
import com.bungae.notification.dto.PushMessage;
import com.bungae.notification.repository.DeviceTokenRepository;
import com.bungae.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock DeviceTokenRepository deviceTokenRepository;
    @Mock PushSender pushSender;
    @InjectMocks PushNotificationService pushNotificationService;

    private final PushMessage message = new PushMessage("제목", "본문", Map.of("type", "NEW_FLASH"));

    @Test
    @DisplayName("대상이 없으면 조회조차 하지 않는다")
    void noTargets() {
        pushNotificationService.pushToUsers(List.of(), message);

        verify(deviceTokenRepository, never()).findByUserIdIn(any());
        verify(pushSender, never()).send(anyList(), any());
    }

    @Test
    @DisplayName("등록된 기기 토큰이 없으면 발송하지 않는다")
    void noDeviceTokens() {
        given(deviceTokenRepository.findByUserIdIn(List.of(1L))).willReturn(List.of());

        pushNotificationService.pushToUsers(List.of(1L), message);

        verify(pushSender, never()).send(anyList(), any());
    }

    @Test
    @DisplayName("모든 기기 토큰으로 발송하고, 죽은 토큰은 정리한다")
    void sendsAndCleansDeadTokens() {
        given(deviceTokenRepository.findByUserIdIn(List.of(1L, 2L)))
                .willReturn(List.of(token(1L, "alive"), token(2L, "dead")));
        given(pushSender.send(List.of("alive", "dead"), message)).willReturn(List.of("dead"));

        pushNotificationService.pushToUsers(List.of(1L, 2L), message);

        verify(pushSender).send(List.of("alive", "dead"), message);
        verify(deviceTokenRepository).deleteByToken("dead");
        verify(deviceTokenRepository, never()).deleteByToken("alive");
    }

    private static DeviceToken token(Long userId, String token) {
        User u = User.builder().nickname("u" + userId).provider("kakao").providerId("pid" + userId).build();
        ReflectionTestUtils.setField(u, "id", userId);
        return DeviceToken.builder().user(u).token(token).platform(Platform.ANDROID).build();
    }
}

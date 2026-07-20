package com.bungae.notification.service;

import com.bungae.notification.domain.DeviceToken;
import com.bungae.notification.domain.DeviceToken.Platform;
import com.bungae.notification.repository.DeviceTokenRepository;
import com.bungae.user.domain.User;
import com.bungae.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    @Mock DeviceTokenRepository deviceTokenRepository;
    @Mock UserRepository userRepository;
    @InjectMocks DeviceTokenService deviceTokenService;

    @Test
    @DisplayName("register: 처음 보는 토큰이면 저장")
    void registerNew() {
        given(deviceTokenRepository.findByToken("t1")).willReturn(Optional.empty());
        given(userRepository.getReferenceById(anyLong())).willAnswer(inv -> user(inv.getArgument(0)));

        deviceTokenService.register(1L, "t1", Platform.ANDROID);

        verify(deviceTokenRepository).save(any(DeviceToken.class));
    }

    @Test
    @DisplayName("register: 이미 내 토큰이면 아무것도 하지 않는다(멱등)")
    void registerIdempotent() {
        given(deviceTokenRepository.findByToken("t1")).willReturn(Optional.of(deviceToken(user(1L), "t1")));

        deviceTokenService.register(1L, "t1", Platform.ANDROID);

        verify(deviceTokenRepository, never()).save(any(DeviceToken.class));
        verify(deviceTokenRepository, never()).delete(any(DeviceToken.class));
    }

    @Test
    @DisplayName("register: 같은 기기에 다른 계정이 로그인하면 기존 소유를 지우고 재등록")
    void registerReassignsDevice() {
        DeviceToken othersToken = deviceToken(user(9L), "t1");
        given(deviceTokenRepository.findByToken("t1")).willReturn(Optional.of(othersToken));
        given(userRepository.getReferenceById(anyLong())).willAnswer(inv -> user(inv.getArgument(0)));

        deviceTokenService.register(1L, "t1", Platform.IOS);

        verify(deviceTokenRepository).delete(othersToken);
        verify(deviceTokenRepository).flush(); // UNIQUE 충돌 방지
        verify(deviceTokenRepository).save(any(DeviceToken.class));
    }

    @Test
    @DisplayName("unregister: 내 토큰이면 삭제")
    void unregisterOwn() {
        DeviceToken mine = deviceToken(user(1L), "t1");
        given(deviceTokenRepository.findByToken("t1")).willReturn(Optional.of(mine));

        deviceTokenService.unregister(1L, "t1");

        verify(deviceTokenRepository).delete(mine);
    }

    @Test
    @DisplayName("unregister: 남의 토큰은 삭제하지 않는다")
    void unregisterOthersIgnored() {
        given(deviceTokenRepository.findByToken("t1")).willReturn(Optional.of(deviceToken(user(9L), "t1")));

        deviceTokenService.unregister(1L, "t1");

        verify(deviceTokenRepository, never()).delete(any(DeviceToken.class));
    }

    private static User user(Long id) {
        User u = User.builder().nickname("u" + id).provider("kakao").providerId("pid" + id).build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static DeviceToken deviceToken(User user, String token) {
        return DeviceToken.builder().user(user).token(token).platform(Platform.ANDROID).build();
    }
}

package com.tuk.notification.service;

import com.tuk.notification.domain.DeviceToken;
import com.tuk.notification.domain.DeviceToken.Platform;
import com.tuk.notification.repository.DeviceTokenRepository;
import com.tuk.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;

    public DeviceTokenService(DeviceTokenRepository deviceTokenRepository, UserRepository userRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.userRepository = userRepository;
    }

    /**
     * 토큰 등록(멱등). token 은 기기 고유(UNIQUE)라서:
     *  - 이미 내 토큰이면 아무것도 안 함
     *  - 같은 기기에 다른 계정이 로그인한 경우면 기존 소유를 지우고 내 것으로 재등록
     */
    @Transactional
    public void register(Long userId, String token, Platform platform) {
        Optional<DeviceToken> existing = deviceTokenRepository.findByToken(token);
        if (existing.isPresent()) {
            DeviceToken deviceToken = existing.get();
            if (deviceToken.getUser().getId().equals(userId)) {
                return;
            }
            deviceTokenRepository.delete(deviceToken);
            deviceTokenRepository.flush(); // UNIQUE(token) 충돌 방지 — 삭제를 먼저 반영
        }
        deviceTokenRepository.save(DeviceToken.builder()
                .user(userRepository.getReferenceById(userId))
                .token(token)
                .platform(platform)
                .build());
    }

    /** 토큰 해제. 내 토큰일 때만 지운다(남의 토큰 삭제 방지). */
    @Transactional
    public void unregister(Long userId, String token) {
        deviceTokenRepository.findByToken(token)
                .filter(deviceToken -> deviceToken.getUser().getId().equals(userId))
                .ifPresent(deviceTokenRepository::delete);
    }
}

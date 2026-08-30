package com.tuk.notification.domain;

import com.tuk.global.common.BaseTimeEntity;
import com.tuk.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FCM 푸시 토큰. user 1 : N device (멀티 기기).
 * 전송 실패(NotRegistered) 시 해당 토큰 삭제.
 */
@Entity
@Table(
    name = "device_tokens",
    uniqueConstraints = @UniqueConstraint(name = "uq_device_token", columnNames = "token")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 255)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Platform platform;

    @Builder
    public DeviceToken(User user, String token, Platform platform) {
        this.user = user;
        this.token = token;
        this.platform = platform;
    }

    public enum Platform { IOS, ANDROID }
}

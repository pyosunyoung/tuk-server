package com.tuk.flash.domain;

import com.tuk.global.common.Activity;
import com.tuk.global.common.BaseTimeEntity;
import com.tuk.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 번개 (즉흥 만남 제안) */
@Entity
@Table(
    name = "flashes",
    indexes = @Index(name = "idx_flash_status_expires", columnList = "status, expiresAt")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class  Flash extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Activity activity;

    /** null = '지금', 값이 있으면 예약 시각(30분/1시간 후) */
    private LocalDateTime scheduledAt;

    @Column(length = 200)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlashStatus status;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Builder
    public Flash(User sender, Activity activity, LocalDateTime scheduledAt,
                 String message, LocalDateTime expiresAt) {
        this.sender = sender;
        this.activity = activity;
        this.scheduledAt = scheduledAt;
        this.message = message;
        this.status = FlashStatus.ACTIVE;
        this.expiresAt = expiresAt;
    }

    public void expire() { this.status = FlashStatus.EXPIRED; }
    public void close()  { this.status = FlashStatus.CLOSED; }

    public boolean isActive() { return this.status == FlashStatus.ACTIVE; }

    public enum FlashStatus { ACTIVE, EXPIRED, CLOSED }
}

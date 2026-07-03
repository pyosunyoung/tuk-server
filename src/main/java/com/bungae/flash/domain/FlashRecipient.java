package com.bungae.flash.domain;

import com.bungae.global.common.BaseTimeEntity;
import com.bungae.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 번개 수신자.
 * status 에 DECLINED(거절)가 의도적으로 없음 → "거절 안 보임"을 스키마로 보장.
 * 무시는 그냥 PENDING 인 채로 만료될 뿐, 보낸 사람은 JOINED 만 조회한다.
 * UNIQUE(flash_id, user_id) → 합류 멱등 처리.
 */
@Entity
@Table(
    name = "flash_recipients",
    uniqueConstraints = @UniqueConstraint(name = "uq_flash_user", columnNames = {"flash_id", "user_id"}),
    indexes = @Index(name = "idx_recipient_user_status", columnList = "user_id, status")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlashRecipient extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flash_id")
    private Flash flash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecipientStatus status;

    private LocalDateTime joinedAt;

    @Builder
    public FlashRecipient(Flash flash, User user) {
        this.flash = flash;
        this.user = user;
        this.status = RecipientStatus.PENDING;
    }

    public void join() {
        this.status = RecipientStatus.JOINED;
        this.joinedAt = LocalDateTime.now();
    }

    public boolean isJoined() { return this.status == RecipientStatus.JOINED; }

    /** 거절(DECLINED) 상태는 일부러 두지 않는다. */
    public enum RecipientStatus { PENDING, JOINED }
}

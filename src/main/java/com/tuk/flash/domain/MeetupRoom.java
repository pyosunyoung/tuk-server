package com.tuk.flash.domain;

import com.tuk.global.common.BaseTimeEntity;
import com.tuk.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 모임방 (Flash 와 1:1).
 * 멤버 = 보낸 사람 + status=JOINED 인 FlashRecipient (별도 멤버 테이블 없음).
 * 장소는 MVP 에선 멤버가 place_name 을 수정. 후보 투표(PlaceCandidate)는 v1.1.
 */
@Entity
@Table(name = "meetup_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetupRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flash_id", unique = true)
    private Flash flash;

    private String placeName;
    private Double placeLat;
    private Double placeLng;

    /**
     * 장소를 정한 멤버. 아직 아무도 안 정했으면 null.
     * 마지막으로 바꾼 사람으로 덮인다(장소는 누구나 다시 정할 수 있다).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_set_by")
    private User placeSetBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Builder
    public MeetupRoom(Flash flash, LocalDateTime expiresAt) {
        this.flash = flash;
        this.status = RoomStatus.OPEN;
        this.expiresAt = expiresAt;
    }

    public void setPlace(String placeName, Double placeLat, Double placeLng, User setter) {
        this.placeName = placeName;
        this.placeLat = placeLat;
        this.placeLng = placeLng;
        this.placeSetBy = setter;
    }

    public void close() { this.status = RoomStatus.CLOSED; }

    public enum RoomStatus { OPEN, CLOSED }
}

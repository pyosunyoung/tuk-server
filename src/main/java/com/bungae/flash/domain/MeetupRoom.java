package com.bungae.flash.domain;

import com.bungae.global.common.BaseTimeEntity;
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

    public void setPlace(String placeName, Double placeLat, Double placeLng) {
        this.placeName = placeName;
        this.placeLat = placeLat;
        this.placeLng = placeLng;
    }

    public void close() { this.status = RoomStatus.CLOSED; }

    public enum RoomStatus { OPEN, CLOSED }
}

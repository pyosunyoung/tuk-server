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
 * 장소는 멤버 누구나 정한다. 검색으로 고르면 카카오 장소 ID·주소·좌표까지, 직접 입력하면 이름만.
 * 후보 투표(PlaceCandidate)는 v1.1.
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

    /** 검색으로 고른 장소의 카카오 장소 ID. 직접 입력한 장소면 null. */
    @Column(length = 30)
    private String placeKakaoId;

    /** 검색으로 고른 장소의 주소(도로명 우선). 직접 입력한 장소면 null. */
    private String placeAddress;

    /**
     * 장소를 정한 멤버. 아직 아무도 안 정했으면 null.
     * 마지막으로 바꾼 사람으로 덮인다(장소는 누구나 다시 정할 수 있다).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_set_by")
    private User placeSetBy;

    /**
     * 만나기로 한 시각. 아직 안 정했으면 null.
     * expiresAt(모집 마감)과는 다른 시각이다 — 약속을 정한다고 만료를 늘리지 않는다.
     */
    private LocalDateTime meetAt;

    /** 약속 시간을 정한 멤버. 장소와 같이 아무나 다시 정할 수 있고, 마지막 사람으로 덮인다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meet_at_set_by")
    private User meetAtSetBy;

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

    /**
     * 장소를 통째로 교체한다. 검색 장소 → 직접 입력으로 바꾸면 ID·주소·좌표가 null 로 지워져야
     * 지도 링크가 이전 가게를 열지 않는다(부분 수정 X).
     */
    public void setPlace(String placeName, Double placeLat, Double placeLng,
                         String placeKakaoId, String placeAddress, User setter) {
        this.placeName = placeName;
        this.placeLat = placeLat;
        this.placeLng = placeLng;
        this.placeKakaoId = placeKakaoId;
        this.placeAddress = placeAddress;
        this.placeSetBy = setter;
    }

    /** 약속 시간을 정한다(다시 정하면 마지막 사람으로 덮인다). */
    public void setMeetAt(LocalDateTime meetAt, User setter) {
        this.meetAt = meetAt;
        this.meetAtSetBy = setter;
    }

    public void close() { this.status = RoomStatus.CLOSED; }

    public enum RoomStatus { OPEN, CLOSED }
}

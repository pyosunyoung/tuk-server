package com.bungae.friendship.domain;

import com.bungae.global.common.BaseTimeEntity;
import com.bungae.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 절친 관계 (자기참조 N:N).
 * 한 쌍당 한 행. 내 친구 조회는 (requester = me OR addressee = me) AND status = ACCEPTED.
 *
 * requester/addressee 는 "누가 신청했나"(수락 권한 판단)를 위해 방향을 보존한다.
 * 하지만 유니크 제약을 (requester,addressee)에 걸면 방향성 때문에 A→B, B→A 두 행이 동시에
 * 생길 수 있다(경합 시 중복 쌍). 그래서 두 userId 의 정규화 쌍(userLowId,userHighId)에
 * UNIQUE 를 걸어 방향 무관하게 DB 레벨에서 쌍당 1행만 허용한다.
 */
@Entity
@Table(
    name = "friendships",
    uniqueConstraints = @UniqueConstraint(name = "uq_friend_pair", columnNames = {"user_low_id", "user_high_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friendship extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id")
    private User requester; // 신청자

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "addressee_id")
    private User addressee; // 수신자

    /** 방향 무관 유니크를 위한 정규화 쌍: min(requesterId, addresseeId) */
    @Column(name = "user_low_id", nullable = false, updatable = false)
    private Long userLowId;

    /** 방향 무관 유니크를 위한 정규화 쌍: max(requesterId, addresseeId) */
    @Column(name = "user_high_id", nullable = false, updatable = false)
    private Long userHighId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendshipStatus status;

    @Builder
    public Friendship(User requester, User addressee) {
        this.requester = requester;
        this.addressee = addressee;
        this.status = FriendshipStatus.PENDING;

        long a = requester.getId();
        long b = addressee.getId();
        this.userLowId = Math.min(a, b);
        this.userHighId = Math.max(a, b);
    }

    public void accept() {
        this.status = FriendshipStatus.ACCEPTED;
    }

    public enum FriendshipStatus { PENDING, ACCEPTED }
}

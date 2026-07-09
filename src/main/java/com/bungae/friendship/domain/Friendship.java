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
 */
@Entity
@Table(
    name = "friendships",
    uniqueConstraints = @UniqueConstraint(name = "uq_friend_pair", columnNames = {"requester_id", "addressee_id"})
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendshipStatus status;

    @Builder
    public Friendship(User requester, User addressee) {
        this.requester = requester;
        this.addressee = addressee;
        this.status = FriendshipStatus.PENDING;
    }

    public void accept() {
        this.status = FriendshipStatus.ACCEPTED;
    }

    public enum FriendshipStatus { PENDING, ACCEPTED }
}

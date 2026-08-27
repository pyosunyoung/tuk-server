package com.bungae.user.domain;

import com.bungae.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "users",
    uniqueConstraints = @UniqueConstraint(name = "uq_user_provider", columnNames = {"provider", "providerId"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String nickname;

    private String profileImage;

    /** 소셜 로그인 제공자: kakao, apple ... */
    @Column(nullable = false, length = 20)
    private String provider;

    @Column(nullable = false)
    private String providerId;

    /**
     * 친구 초대코드. 최초 조회 시 지연 생성된다(nullable).
     * ddl-auto:update 는 기존 테이블에 유니크 제약을 새로 걸어주지 않으므로,
     * 유일성은 애플리케이션 레벨(생성 시 재시도 + 코드 조회)에서 보장한다.
     */
    @Column(length = 16)
    private String inviteCode;

    @Builder
    public User(String nickname, String profileImage, String provider, String providerId) {
        this.nickname = nickname;
        this.profileImage = profileImage;
        this.provider = provider;
        this.providerId = providerId;
    }

    public void updateProfile(String nickname, String profileImage) {
        this.nickname = nickname;
        this.profileImage = profileImage;
    }

    public void assignInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }
}

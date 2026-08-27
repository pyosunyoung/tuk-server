package com.bungae.user.repository;

import com.bungae.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 소셜 로그인: (provider, providerId)로 기존 회원 조회. 없으면 신규 가입. */
    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    boolean existsByProviderAndProviderId(String provider, String providerId);

    /** 친구 초대코드로 사용자 조회. */
    Optional<User> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);
}

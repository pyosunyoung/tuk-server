package com.tuk.notification.repository;

import com.tuk.notification.domain.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    /** 기기 토큰 등록 시 중복 확인(upsert). token 은 UNIQUE. */
    Optional<DeviceToken> findByToken(String token);

    /** 특정 사용자의 모든 기기 토큰(멀티 기기 푸시 대상). */
    List<DeviceToken> findByUserId(Long userId);

    /** 여러 사용자에게 한 번에 푸시할 때(예: 번개 수신자들). */
    List<DeviceToken> findByUserIdIn(Collection<Long> userIds);

    /** FCM 이 NotRegistered 를 응답한 죽은 토큰 정리. */
    void deleteByToken(String token);

    /** 탈퇴 — 그 사용자의 모든 기기 토큰. 탈퇴한 기기로 남의 알림이 가면 안 된다. */
    @Modifying
    @Query("delete from DeviceToken d where d.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}

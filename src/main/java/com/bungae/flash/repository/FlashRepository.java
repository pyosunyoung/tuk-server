package com.bungae.flash.repository;

import com.bungae.flash.domain.Flash;
import com.bungae.flash.domain.Flash.FlashStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface FlashRepository extends JpaRepository<Flash, Long> {

    /**
     * 만료 스위퍼용: ACTIVE 인데 expiresAt 이 지난 번개.
     * 조회한 뒤 각 flash.expire() 를 호출한다(상태 변경은 도메인 메서드로 — 벌크 update 지양).
     * (status, expiresAt) 복합 인덱스가 이 조회를 커버한다.
     */
    List<Flash> findByStatusAndExpiresAtBefore(FlashStatus status, LocalDateTime time);

    /** 내가 띄운 활성 번개: 내가 보낸(sender) + ACTIVE + 만료 전. 최신순. */
    List<Flash> findBySenderIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
            Long senderId, FlashStatus status, LocalDateTime now);
}

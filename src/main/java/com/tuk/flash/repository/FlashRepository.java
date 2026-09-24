package com.tuk.flash.repository;

import com.tuk.flash.domain.Flash;
import com.tuk.flash.domain.Flash.FlashStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FlashRepository extends JpaRepository<Flash, Long> {

    /**
     * 만료 스위퍼용: ACTIVE 인데 expiresAt 이 지난 번개.
     * 조회한 뒤 각 flash.expire() 를 호출한다(상태 변경은 도메인 메서드로 — 벌크 update 지양).
     * (status, expiresAt) 복합 인덱스가 이 조회를 커버한다.
     */
    List<Flash> findByStatusAndExpiresAtBefore(FlashStatus status, LocalDateTime time);

    /**
     * 홈에 아직 보여야 할, 내가 띄운 툭. 최신순.
     *
     * 기준은 "합류 마감이 안 지났거나, **약속 시간이 아직 남았거나**" 다.
     * 합류가 끝나도 약속 시간까지는 어디서 몇 시에 만나는지 다시 볼 수 있어야 한다
     * (모임방은 만료돼도 지워지지 않는다 — status 만 CLOSED 가 된다).
     */
    @Query("""
            select f from Flash f
            where f.sender.id = :senderId
              and ((f.status = :status and f.expiresAt > :now)
                   or exists (select 1 from MeetupRoom r where r.flash = f and r.meetAt > :now))
            order by f.createdAt desc
            """)
    List<Flash> findSentStillRelevant(@Param("senderId") Long senderId,
                                      @Param("status") FlashStatus status,
                                      @Param("now") LocalDateTime now);
}

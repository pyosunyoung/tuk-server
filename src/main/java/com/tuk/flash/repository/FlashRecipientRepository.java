package com.tuk.flash.repository;

import com.tuk.flash.domain.Flash.FlashStatus;
import com.tuk.flash.domain.FlashRecipient;
import com.tuk.flash.domain.FlashRecipient.RecipientStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FlashRecipientRepository extends JpaRepository<FlashRecipient, Long> {

    /** 멱등 합류: (flash, user)로 기존 수신 레코드 조회. UNIQUE(flash_id, user_id) 로 최대 1건. */
    Optional<FlashRecipient> findByFlashIdAndUserId(Long flashId, Long userId);

    /** 모임방 멤버 = 특정 번개의 JOINED 수신자(별도 멤버 테이블 없음). */
    List<FlashRecipient> findByFlashIdAndStatus(Long flashId, RecipientStatus status);

    /**
     * 목록 화면용 배치 조회. flash 마다 수신자를 따로 조회하면 N+1 이 된다.
     * user 를 fetch join 해서 닉네임까지 한 번에 가져온다.
     */
    @Query("""
            select fr from FlashRecipient fr
            join fetch fr.user
            where fr.flash.id in :flashIds
            """)
    List<FlashRecipient> findByFlashIdIn(@Param("flashIds") Collection<Long> flashIds);

    /**
     * "내가 받은 활성 번개": ACTIVE + 아직 만료 전인 번개만.
     * DECLINED 개념이 없으므로 무시한 번개도 PENDING 으로 함께 보이지만, 만료되면 자연히 사라진다.
     * 발신자(sender)까지 fetch join → 목록 화면에서 N+1 방지.
     */
    @Query("""
            select fr from FlashRecipient fr
            join fetch fr.flash f
            join fetch f.sender
            where fr.user.id = :userId
              and ((f.status = :status and f.expiresAt > :now)
                   or (fr.status = com.tuk.flash.domain.FlashRecipient$RecipientStatus.JOINED
                       and exists (select 1 from MeetupRoom r where r.flash = f and r.meetAt > :now)))
            order by f.createdAt desc
            """)
    List<FlashRecipient> findReceived(@Param("userId") Long userId,
                                      @Param("status") FlashStatus status,
                                      @Param("now") LocalDateTime now);

    /**
     * 합류 마감이 지나도 **내가 합류한** 모임은 약속 시간까지 계속 보인다 — 어디서 몇 시인지 다시 봐야 하므로.
     *
     * 합류 연장은 **JOINED 에만** 적용한다. 보낸 사람도 멤버라 약속 시간을 잡을 수 있는데,
     * 그걸 근거로 PENDING 까지 남기면 **합류도 못 하는 죽은 초대가 목록에 계속 뜬다**
     * ("합류하기"를 눌러도 400). 실제로 그랬다.
     */
    default List<FlashRecipient> findActiveReceived(Long userId, LocalDateTime now) {
        return findReceived(userId, FlashStatus.ACTIVE, now);
    }
}

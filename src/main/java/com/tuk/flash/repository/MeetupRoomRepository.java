package com.tuk.flash.repository;

import com.tuk.flash.domain.MeetupRoom;
import com.tuk.flash.domain.MeetupRoom.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MeetupRoomRepository extends JpaRepository<MeetupRoom, Long> {

    /** Flash 와 1:1 → flashId 로 모임방 조회. */
    Optional<MeetupRoom> findByFlashId(Long flashId);

    /** 목록 화면용 배치 조회 — flashId 마다 방을 따로 조회하면 N+1 이 된다. */
    List<MeetupRoom> findByFlashIdIn(Collection<Long> flashIds);

    /** 만료 스위퍼용: OPEN 인데 expiresAt 이 지난 모임방. */
    List<MeetupRoom> findByStatusAndExpiresAtBefore(RoomStatus status, LocalDateTime time);

    // ── 탈퇴 (AccountDeletionService) ──

    /** 남의 모임방에서 탈퇴자가 "장소 정한 사람"으로 남은 기록만 비운다(모임방은 그대로). */
    @Modifying
    @Query("update MeetupRoom r set r.placeSetBy = null where r.placeSetBy.id = :userId")
    int clearPlaceSetBy(@Param("userId") Long userId);

    @Modifying
    @Query("update MeetupRoom r set r.meetAtSetBy = null where r.meetAtSetBy.id = :userId")
    int clearMeetAtSetBy(@Param("userId") Long userId);

    /** 탈퇴자가 띄운 툭의 모임방. */
    @Modifying
    @Query("delete from MeetupRoom r where r.flash.id in (select f.id from Flash f where f.sender.id = :userId)")
    int deleteBySender(@Param("userId") Long userId);
}

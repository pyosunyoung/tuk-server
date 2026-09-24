package com.tuk.flash.repository;

import com.tuk.flash.domain.MeetupRoom;
import com.tuk.flash.domain.MeetupRoom.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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
}

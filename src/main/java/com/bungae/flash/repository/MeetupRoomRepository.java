package com.bungae.flash.repository;

import com.bungae.flash.domain.MeetupRoom;
import com.bungae.flash.domain.MeetupRoom.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MeetupRoomRepository extends JpaRepository<MeetupRoom, Long> {

    /** Flash 와 1:1 → flashId 로 모임방 조회. */
    Optional<MeetupRoom> findByFlashId(Long flashId);

    /** 만료 스위퍼용: OPEN 인데 expiresAt 이 지난 모임방. */
    List<MeetupRoom> findByStatusAndExpiresAtBefore(RoomStatus status, LocalDateTime time);
}

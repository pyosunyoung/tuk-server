package com.tuk.flash.scheduler;

import com.tuk.flash.domain.Flash;
import com.tuk.flash.domain.MeetupRoom;
import com.tuk.flash.repository.FlashRepository;
import com.tuk.flash.repository.MeetupRoomRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 만료 스위퍼(원칙 2, 자동 만료). expires_at 지난 ACTIVE 번개 / OPEN 모임방을 주기적으로 만료·종료.
 * 상태 변경은 도메인 메서드(expire/close) + 트랜잭션 dirty checking 으로 반영(벌크 update 지양).
 */
@Component
public class FlashExpirationScheduler {

    private final FlashRepository flashRepository;
    private final MeetupRoomRepository meetupRoomRepository;

    public FlashExpirationScheduler(FlashRepository flashRepository,
                                    MeetupRoomRepository meetupRoomRepository) {
        this.flashRepository = flashRepository;
        this.meetupRoomRepository = meetupRoomRepository;
    }

    @Scheduled(
            fixedDelayString = "${flash.expiration.sweep-interval-ms:60000}",
            initialDelayString = "${flash.expiration.initial-delay-ms:60000}")
    @Transactional
    public void sweep() {
        LocalDateTime now = LocalDateTime.now();
        flashRepository.findByStatusAndExpiresAtBefore(Flash.FlashStatus.ACTIVE, now)
                .forEach(Flash::expire);
         //DB에서 상태가 ACTIVE이면서 만료 시간이 현재 시간(now)보다 이전인 번개방 목록 가져와서 모두 expire로 바꿔주는 로직
        meetupRoomRepository.findByStatusAndExpiresAtBefore(MeetupRoom.RoomStatus.OPEN, now)
                .forEach(MeetupRoom::close);
    }
}

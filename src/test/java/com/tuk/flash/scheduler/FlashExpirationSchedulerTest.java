package com.tuk.flash.scheduler;

import com.tuk.flash.domain.Flash;
import com.tuk.flash.domain.MeetupRoom;
import com.tuk.flash.repository.FlashRepository;
import com.tuk.flash.repository.MeetupRoomRepository;
import com.tuk.global.common.Activity;
import com.tuk.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FlashExpirationSchedulerTest {

    @Mock FlashRepository flashRepository;
    @Mock MeetupRoomRepository meetupRoomRepository;
    @InjectMocks FlashExpirationScheduler scheduler;

    @Test
    @DisplayName("sweep: 만료된 ACTIVE 번개는 EXPIRED, OPEN 모임방은 CLOSED 로 전환")
    void sweepExpiresFlashesAndRooms() {
        Flash flash = Flash.builder()
                .sender(user()).activity(Activity.MEAL)
                .expiresAt(LocalDateTime.now().minusMinutes(1)).build();
        MeetupRoom room = MeetupRoom.builder()
                .flash(flash).expiresAt(LocalDateTime.now().minusMinutes(1)).build();

        given(flashRepository.findByStatusAndExpiresAtBefore(eq(Flash.FlashStatus.ACTIVE), any()))
                .willReturn(List.of(flash));
        given(meetupRoomRepository.findByStatusAndExpiresAtBefore(eq(MeetupRoom.RoomStatus.OPEN), any()))
                .willReturn(List.of(room));

        scheduler.sweep();

        assertThat(flash.getStatus()).isEqualTo(Flash.FlashStatus.EXPIRED);
        assertThat(room.getStatus()).isEqualTo(MeetupRoom.RoomStatus.CLOSED);
    }

    private static User user() {
        User u = User.builder().nickname("보낸이").provider("kakao").providerId("pid").build();
        ReflectionTestUtils.setField(u, "id", 1L);
        return u;
    }
}

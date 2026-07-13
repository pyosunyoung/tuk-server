package com.bungae.flash.service;

import com.bungae.flash.domain.Flash;
import com.bungae.flash.domain.FlashRecipient;
import com.bungae.flash.domain.FlashRecipient.RecipientStatus;
import com.bungae.flash.domain.MeetupRoom;
import com.bungae.flash.dto.FlashResponse;
import com.bungae.flash.dto.MeetupRoomResponse;
import com.bungae.flash.dto.SendFlashRequest;
import com.bungae.flash.dto.SetPlaceRequest;
import com.bungae.flash.repository.FlashRecipientRepository;
import com.bungae.flash.repository.FlashRepository;
import com.bungae.flash.repository.MeetupRoomRepository;
import com.bungae.friendship.repository.FriendshipRepository;
import com.bungae.global.common.Activity;
import com.bungae.global.exception.BadRequestException;
import com.bungae.global.exception.ForbiddenException;
import com.bungae.user.domain.User;
import com.bungae.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FlashServiceTest {

    @Mock FlashRepository flashRepository;
    @Mock FlashRecipientRepository flashRecipientRepository;
    @Mock MeetupRoomRepository meetupRoomRepository;
    @Mock FriendshipRepository friendshipRepository;
    @Mock UserRepository userRepository;
    @InjectMocks FlashService flashService;

    // --- 발송 ---

    @Test
    @DisplayName("sendFlash: 대상 중 절친이 아닌 사람은 제외(서버측 재검증)")
    void sendReValidatesTargets() {
        given(friendshipRepository.findAcceptedFriendIds(1L)).willReturn(List.of(2L, 3L));
        given(userRepository.getReferenceById(anyLong())).willAnswer(inv -> user(inv.getArgument(0), "u"));
        given(flashRepository.save(any(Flash.class))).willAnswer(inv -> withId(inv.getArgument(0), 10L));
        given(flashRecipientRepository.save(any(FlashRecipient.class))).willAnswer(inv -> inv.getArgument(0));
        given(meetupRoomRepository.save(any(MeetupRoom.class))).willAnswer(inv -> withId(inv.getArgument(0), 20L));

        // 대상에 절친 아님(4) 포함
        FlashResponse res = flashService.sendFlash(1L, request(List.of(2L, 3L, 4L), null));

        assertThat(res.flashId()).isEqualTo(10L);
        assertThat(res.roomId()).isEqualTo(20L);
        assertThat(res.recipientCount()).isEqualTo(2); // 4 제외
        verify(flashRecipientRepository, times(2)).save(any(FlashRecipient.class));
    }

    @Test
    @DisplayName("sendFlash: 유효한 절친 대상이 하나도 없으면 400")
    void sendNoValidTargets() {
        given(friendshipRepository.findAcceptedFriendIds(1L)).willReturn(List.of(2L, 3L));

        assertThatThrownBy(() -> flashService.sendFlash(1L, request(List.of(9L), null)))
                .isInstanceOf(BadRequestException.class);
        verify(flashRepository, never()).save(any());
    }

    @Test
    @DisplayName("sendFlash: 예약 시각이 과거면 400")
    void sendScheduledInPast() {
        assertThatThrownBy(() -> flashService.sendFlash(1L, request(List.of(2L), LocalDateTime.now().minusHours(1))))
                .isInstanceOf(BadRequestException.class);
        verify(flashRepository, never()).save(any());
    }

    // --- 합류 ---

    @Test
    @DisplayName("join: 초대받지 않은(수신 레코드 없음) 사람은 403")
    void joinNotInvited() {
        given(flashRecipientRepository.findByFlashIdAndUserId(10L, 2L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> flashService.join(2L, 10L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("join: 만료/종료된 번개는 400")
    void joinExpiredFlash() {
        Flash flash = flash(10L, user(1L, "보낸이"));
        flash.expire();
        FlashRecipient recipient = FlashRecipient.builder().flash(flash).user(user(2L, "받는이")).build();
        given(flashRecipientRepository.findByFlashIdAndUserId(10L, 2L)).willReturn(Optional.of(recipient));

        assertThatThrownBy(() -> flashService.join(2L, 10L)).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("join: 초대받은 사람이 합류하면 JOINED + 모임방(멤버=보낸이+합류자) 반환")
    void joinSuccess() {
        Flash flash = flash(10L, user(1L, "보낸이"));
        FlashRecipient recipient = FlashRecipient.builder().flash(flash).user(user(2L, "받는이")).build();
        given(flashRecipientRepository.findByFlashIdAndUserId(10L, 2L)).willReturn(Optional.of(recipient));
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room(20L, flash)));
        given(flashRecipientRepository.findByFlashIdAndStatus(10L, RecipientStatus.JOINED))
                .willReturn(List.of(recipient));

        MeetupRoomResponse res = flashService.join(2L, 10L);

        assertThat(recipient.isJoined()).isTrue();
        assertThat(res.members()).extracting("userId").containsExactlyInAnyOrder(1L, 2L);
        assertThat(res.members()).filteredOn("sender", true).extracting("userId").containsExactly(1L);
    }

    @Test
    @DisplayName("join: 이미 합류한 사람이 다시 눌러도(멱등) 예외 없이 방 반환")
    void joinIdempotent() {
        Flash flash = flash(10L, user(1L, "보낸이"));
        FlashRecipient recipient = FlashRecipient.builder().flash(flash).user(user(2L, "받는이")).build();
        recipient.join(); // 이미 JOINED
        given(flashRecipientRepository.findByFlashIdAndUserId(10L, 2L)).willReturn(Optional.of(recipient));
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room(20L, flash)));
        given(flashRecipientRepository.findByFlashIdAndStatus(10L, RecipientStatus.JOINED))
                .willReturn(List.of(recipient));

        MeetupRoomResponse res = flashService.join(2L, 10L);

        assertThat(recipient.isJoined()).isTrue();
        assertThat(res.roomId()).isEqualTo(20L);
    }

    // --- 모임방 ---

    @Test
    @DisplayName("getRoom: 멤버(보낸이/합류자)가 아니면 403")
    void getRoomNonMember() {
        Flash flash = flash(10L, user(1L, "보낸이"));
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room(20L, flash)));
        given(flashRecipientRepository.findByFlashIdAndUserId(10L, 99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> flashService.getRoom(99L, 10L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("setPlace: 멤버(보낸이)면 장소를 설정한다")
    void setPlaceBySender() {
        Flash flash = flash(10L, user(1L, "보낸이"));
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room(20L, flash)));
        given(flashRecipientRepository.findByFlashIdAndStatus(10L, RecipientStatus.JOINED)).willReturn(List.of());

        MeetupRoomResponse res = flashService.setPlace(1L, 10L, new SetPlaceRequest("홍대 A카페", 37.5, 127.0));

        assertThat(res.placeName()).isEqualTo("홍대 A카페");
    }

    // --- helpers ---

    private static SendFlashRequest request(List<Long> targets, LocalDateTime scheduledAt) {
        return new SendFlashRequest(Activity.MEAL, "밥 먹자", scheduledAt, null, targets);
    }

    private static User user(Long id, String nickname) {
        User u = User.builder().nickname(nickname).provider("kakao").providerId("pid" + id).build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static Flash flash(Long id, User sender) {
        Flash f = Flash.builder()
                .sender(sender).activity(Activity.MEAL)
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
        return withId(f, id);
    }

    private static MeetupRoom room(Long id, Flash flash) {
        MeetupRoom r = MeetupRoom.builder().flash(flash).expiresAt(LocalDateTime.now().plusHours(1)).build();
        return withId(r, id);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}

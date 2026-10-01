package com.tuk.flash.service;

import com.tuk.flash.domain.Flash;
import com.tuk.flash.domain.FlashRecipient;
import com.tuk.flash.domain.FlashRecipient.RecipientStatus;
import com.tuk.flash.domain.MeetupRoom;
import com.tuk.flash.dto.FlashPushEvent;
import com.tuk.flash.dto.FlashPushType;
import com.tuk.flash.dto.FlashResponse;
import com.tuk.flash.dto.MeetupRoomResponse;
import com.tuk.flash.dto.SetMeetAtRequest;
import com.tuk.flash.dto.SendFlashRequest;
import com.tuk.flash.dto.SentFlashResponse;
import com.tuk.flash.dto.SetPlaceRequest;
import com.tuk.flash.repository.FlashRecipientRepository;
import com.tuk.flash.repository.FlashRepository;
import com.tuk.flash.repository.MeetupRoomRepository;
import com.tuk.flash.ws.FlashBroadcastEvent;
import com.tuk.friendship.repository.FriendshipRepository;
import com.tuk.global.common.Activity;
import com.tuk.global.exception.BadRequestException;
import com.tuk.global.exception.ForbiddenException;
import com.tuk.user.domain.User;
import com.tuk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks FlashService flashService;

    // --- 발송 ---

    @Test
    @DisplayName("sendFlash: 대상 중 절친이 아닌 사람은 제외(서버측 재검증)")
    void sendReValidatesTargets() {
        given(friendshipRepository.findAcceptedFriendIds(1L)).willReturn(List.of(2L, 3L));
        given(userRepository.findById(1L)).willReturn(Optional.of(user(1L, "보낸이")));
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
        // 커밋 이후 전파를 위해 이벤트 발행
        verify(eventPublisher).publishEvent(any(FlashBroadcastEvent.class));
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
        // 최초 합류 → 나머지 멤버에게 전파 이벤트 발행
        verify(eventPublisher).publishEvent(any(FlashBroadcastEvent.class));
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
        // 멱등 재호출은 전파하지 않는다
        verify(eventPublisher, never()).publishEvent(any());
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
    @DisplayName("setPlace: 멤버(보낸이)면 장소를 설정하고, 정한 사람을 함께 돌려준다")
    void setPlaceBySender() {
        User sender = user(1L, "보낸이");
        Flash flash = flash(10L, sender);
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room(20L, flash)));
        given(flashRecipientRepository.findByFlashIdAndStatus(10L, RecipientStatus.JOINED)).willReturn(List.of());
        given(userRepository.getReferenceById(1L)).willReturn(sender);

        MeetupRoomResponse res = flashService.setPlace(1L, 10L, new SetPlaceRequest("홍대 A카페", 37.5, 127.0, null, null));

        assertThat(res.placeName()).isEqualTo("홍대 A카페");
        assertThat(res.placeSetById()).isEqualTo(1L);
        assertThat(res.placeSetByNickname()).isEqualTo("보낸이");
    }

    @Test
    @DisplayName("setPlace: 합류자가 정하면 그 사람이 '정한 사람'이 된다(마지막에 바꾼 사람으로 덮임)")
    void setPlaceByJoiner() {
        Flash flash = flash(10L, user(1L, "보낸이"));
        User joiner = user(2L, "합류자");
        FlashRecipient joined = FlashRecipient.builder().flash(flash).user(joiner).build();
        joined.join();
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room(20L, flash)));
        given(flashRecipientRepository.findByFlashIdAndUserId(10L, 2L)).willReturn(Optional.of(joined));
        given(flashRecipientRepository.findByFlashIdAndStatus(10L, RecipientStatus.JOINED)).willReturn(List.of(joined));
        given(userRepository.getReferenceById(2L)).willReturn(joiner);

        MeetupRoomResponse res = flashService.setPlace(2L, 10L, new SetPlaceRequest("학관 앞"));

        assertThat(res.placeSetById()).isEqualTo(2L);
        assertThat(res.placeSetByNickname()).isEqualTo("합류자");
    }

    @Test
    @DisplayName("setPlace: 검색으로 고른 장소는 카카오 장소 ID·주소·좌표까지 저장해 응답한다")
    void setSearchedPlace() {
        User sender = user(1L, "보낸이");
        Flash flash = flash(10L, sender);
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room(20L, flash)));
        given(flashRecipientRepository.findByFlashIdAndStatus(10L, RecipientStatus.JOINED)).willReturn(List.of());
        given(userRepository.getReferenceById(1L)).willReturn(sender);

        MeetupRoomResponse res = flashService.setPlace(1L, 10L, new SetPlaceRequest(
                "스타벅스 강남역신분당역사점", 37.4979, 127.0276, "1234567", "서울 강남구 강남대로 396"));

        assertThat(res.placeKakaoId()).isEqualTo("1234567");
        assertThat(res.placeAddress()).isEqualTo("서울 강남구 강남대로 396");
        assertThat(res.placeLat()).isEqualTo(37.4979);
    }

    @Test
    @DisplayName("setPlace: 검색 장소 → 직접 입력으로 바꾸면 이전 가게의 ID·주소·좌표가 지워진다")
    void manualPlaceClearsSearchedDetails() {
        User sender = user(1L, "보낸이");
        Flash flash = flash(10L, sender);
        MeetupRoom room = room(20L, flash);
        room.setPlace("스타벅스 강남", 37.4979, 127.0276, "1234567", "서울 강남구 강남대로 396", sender);
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room));
        given(flashRecipientRepository.findByFlashIdAndStatus(10L, RecipientStatus.JOINED)).willReturn(List.of());
        given(userRepository.getReferenceById(1L)).willReturn(sender);

        MeetupRoomResponse res = flashService.setPlace(1L, 10L, new SetPlaceRequest("  학교 정문  "));

        assertThat(res.placeName()).isEqualTo("학교 정문");
        assertThat(res.placeKakaoId()).isNull();
        assertThat(res.placeAddress()).isNull();
        assertThat(res.placeLat()).isNull();
        assertThat(res.placeLng()).isNull();
    }

    @Test
    @DisplayName("getRoom: 아무도 장소를 안 정했으면 정한 사람은 null")
    void roomWithoutPlaceHasNoSetter() {
        Flash flash = flash(10L, user(1L, "보낸이"));
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room(20L, flash)));
        given(flashRecipientRepository.findByFlashIdAndStatus(10L, RecipientStatus.JOINED)).willReturn(List.of());

        MeetupRoomResponse res = flashService.getRoom(1L, 10L);

        assertThat(res.placeSetById()).isNull();
        assertThat(res.placeSetByNickname()).isNull();
    }

    @Test
    @DisplayName("getRoom: 공유 링크로 바로 들어와도 만료 시각을 알 수 있게 방 응답에 담는다")
    void roomCarriesExpiresAt() {
        Flash flash = flash(10L, user(1L, "보낸이"));
        MeetupRoom room = room(20L, flash);
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room));
        given(flashRecipientRepository.findByFlashIdAndStatus(10L, RecipientStatus.JOINED)).willReturn(List.of());

        MeetupRoomResponse res = flashService.getRoom(1L, 10L);

        assertThat(res.expiresAt()).isEqualTo(room.getExpiresAt());
    }

    // --- 약속 시간 (PATCH /room/time) ---

    @Test
    @DisplayName("setMeetAt: 멤버면 약속 시간을 정할 수 있고, 정한 사람이 남는다")
    void setMeetAtByMember() {
        User sender = user(1L, "보낸이");
        Flash flash = flash(10L, sender);
        MeetupRoom room = room(20L, flash);
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room));
        given(flashRecipientRepository.findByFlashIdAndStatus(10L, RecipientStatus.JOINED)).willReturn(List.of());
        given(userRepository.getReferenceById(1L)).willReturn(sender);

        LocalDateTime when = LocalDateTime.now().plusMinutes(90);
        MeetupRoomResponse res = flashService.setMeetAt(1L, 10L, new SetMeetAtRequest(when));

        assertThat(res.meetAt()).isEqualTo(when);
        assertThat(res.meetAtSetById()).isEqualTo(1L);
        assertThat(res.meetAtSetByNickname()).isEqualTo("보낸이");
    }

    @Test
    @DisplayName("setMeetAt: 약속 시간을 정해도 만료(모집 마감)는 늘어나지 않는다")
    void setMeetAtDoesNotExtendExpiry() {
        User sender = user(1L, "보낸이");
        Flash flash = flash(10L, sender);
        MeetupRoom room = room(20L, flash);
        LocalDateTime before = room.getExpiresAt();
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room));
        given(flashRecipientRepository.findByFlashIdAndStatus(10L, RecipientStatus.JOINED)).willReturn(List.of());
        given(userRepository.getReferenceById(1L)).willReturn(sender);

        // 만료보다 한참 뒤로 약속을 잡아도 만료는 그대로여야 한다
        flashService.setMeetAt(1L, 10L, new SetMeetAtRequest(LocalDateTime.now().plusHours(5)));

        assertThat(room.getExpiresAt()).isEqualTo(before);
    }

    @Test
    @DisplayName("setMeetAt: 멤버가 아니면 403")
    void setMeetAtNonMember() {
        Flash flash = flash(10L, user(1L, "보낸이"));
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room(20L, flash)));
        given(flashRecipientRepository.findByFlashIdAndUserId(10L, 99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> flashService.setMeetAt(99L, 10L,
                new SetMeetAtRequest(LocalDateTime.now().plusHours(1))))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("setMeetAt: 이미 지난 시간은 거부 (정각 클릭은 봐준다)")
    void setMeetAtRejectsPast() {
        User sender = user(1L, "보낸이");
        Flash flash = flash(10L, sender);
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room(20L, flash)));
        given(flashRecipientRepository.findByFlashIdAndStatus(10L, RecipientStatus.JOINED)).willReturn(List.of());
        given(userRepository.getReferenceById(1L)).willReturn(sender);

        assertThatThrownBy(() -> flashService.setMeetAt(1L, 10L,
                new SetMeetAtRequest(LocalDateTime.now().minusHours(3))))
                .isInstanceOf(BadRequestException.class);

        // 방금 지난 시각은 통과해야 한다 — "8시"를 8시 정각에 누르는 경우
        assertThatCode(() -> flashService.setMeetAt(1L, 10L,
                new SetMeetAtRequest(LocalDateTime.now().minusMinutes(1))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("setMeetAt: 하루를 넘는 시간은 거부")
    void setMeetAtRejectsTooFar() {
        Flash flash = flash(10L, user(1L, "보낸이"));
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room(20L, flash)));

        assertThatThrownBy(() -> flashService.setMeetAt(1L, 10L,
                new SetMeetAtRequest(LocalDateTime.now().plusDays(2))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("getRoom: 아직 안 정했으면 약속 시간은 null")
    void roomWithoutMeetAt() {
        Flash flash = flash(10L, user(1L, "보낸이"));
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room(20L, flash)));
        given(flashRecipientRepository.findByFlashIdAndStatus(10L, RecipientStatus.JOINED)).willReturn(List.of());

        MeetupRoomResponse res = flashService.getRoom(1L, 10L);

        assertThat(res.meetAt()).isNull();
        assertThat(res.meetAtSetById()).isNull();
        assertThat(res.meetAtSetByNickname()).isNull();
    }

    // --- 내가 띄운 번개 (GET /sent) ---

    @Test
    @DisplayName("getSentFlashes: 내 활성 번개를 합류자와 함께 반환")
    void sentWithJoinedMembers() {
        Flash flash = flash(10L, user(1L, "보낸이"));
        FlashRecipient joined = FlashRecipient.builder().flash(flash).user(user(2L, "합류자")).build();
        joined.join();
        FlashRecipient pending = FlashRecipient.builder().flash(flash).user(user(3L, "안온사람")).build();
        given(flashRepository.findSentStillRelevant(anyLong(), any(Flash.FlashStatus.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(List.of(flash));
        given(flashRecipientRepository.findByFlashIdIn(List.of(10L)))
                .willReturn(List.of(joined, pending));

        List<SentFlashResponse> res = flashService.getSentFlashes(1L);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).flashId()).isEqualTo(10L);
        // 합류자만 모임방 멤버로
        assertThat(res.get(0).joinedMembers()).extracting("nickname").containsExactly("합류자");
        // recipientIds 는 합류 여부와 무관하게 초대받은 전부 — 중복 발송 안내에 쓴다
        assertThat(res.get(0).recipientIds()).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    @DisplayName("getSentFlashes: 아직 도달 안 한 예약 번개는 제외(방 열리기 전)")
    void sentExcludesFutureReserved() {
        Flash reserved = withId(Flash.builder()
                .sender(user(1L, "보낸이")).activity(Activity.MEAL)
                .scheduledAt(LocalDateTime.now().plusHours(1))
                .expiresAt(LocalDateTime.now().plusHours(2)).build(), 11L);
        given(flashRepository.findSentStillRelevant(anyLong(), any(Flash.FlashStatus.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(List.of(reserved));

        List<SentFlashResponse> res = flashService.getSentFlashes(1L);

        assertThat(res).isEmpty();
        // 제외됐으므로 멤버 조회도 하지 않는다
        verify(flashRecipientRepository, never()).findByFlashIdAndStatus(anyLong(), any());
    }

    @Test
    @DisplayName("getSentFlashes: 활성 발송 번개가 없으면 빈 목록")
    void sentEmpty() {
        given(flashRepository.findSentStillRelevant(anyLong(), any(Flash.FlashStatus.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(List.of());

        assertThat(flashService.getSentFlashes(1L)).isEmpty();
    }

    // --- 장소·시간 정함/바꿈 알림 ---

    @Test
    @DisplayName("setPlace: 처음 정하면 정한 사람을 뺀 멤버에게 PLACE_UPDATED(이전 값 없음)")
    void setPlaceFirstTimeNotifiesOthers() {
        User sender = user(1L, "보낸이");
        Flash flash = flash(10L, sender);
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room(20L, flash)));
        givenJoined(flash, user(2L, "합류자"));
        given(userRepository.getReferenceById(1L)).willReturn(sender);

        flashService.setPlace(1L, 10L, new SetPlaceRequest("홍대입구역"));

        FlashBroadcastEvent event = publishedEvent();
        assertThat(event.targetUserIds()).containsExactly(2L);
        assertThat(event.payload().type()).isEqualTo(FlashPushType.PLACE_UPDATED);
        assertThat(event.payload().nickname()).isEqualTo("보낸이");
        assertThat(event.payload().placeBefore()).isNull();
        assertThat(event.payload().placeAfter()).isEqualTo("홍대입구역");
    }

    @Test
    @DisplayName("setPlace: 바꾸면 이전 장소를 함께 보낸다")
    void setPlaceChangeCarriesBefore() {
        User sender = user(1L, "보낸이");
        User joiner = user(2L, "합류자");
        Flash flash = flash(10L, sender);
        MeetupRoom room = room(20L, flash);
        room.setPlace("홍대입구역", null, null, null, null, sender);
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room));
        FlashRecipient joined = givenJoined(flash, joiner);
        given(flashRecipientRepository.findByFlashIdAndUserId(10L, 2L)).willReturn(Optional.of(joined));
        given(userRepository.getReferenceById(2L)).willReturn(joiner);

        flashService.setPlace(2L, 10L, new SetPlaceRequest("합정역"));

        FlashBroadcastEvent event = publishedEvent();
        assertThat(event.targetUserIds()).containsExactly(1L); // 바꾼 사람(2)은 빠진다
        assertThat(event.payload().placeBefore()).isEqualTo("홍대입구역");
        assertThat(event.payload().placeAfter()).isEqualTo("합정역");
    }

    @Test
    @DisplayName("setPlace: 같은 장소를 다시 고르면 알리지 않는다")
    void setSamePlaceIsSilent() {
        User sender = user(1L, "보낸이");
        Flash flash = flash(10L, sender);
        MeetupRoom room = room(20L, flash);
        room.setPlace("스타벅스 강남R점", 37.49, 127.02, "35026031", "서울 강남구", sender);
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room));
        givenJoined(flash, user(2L, "합류자"));
        // 같은 값이면 방을 건드리지도 않으므로 정한 사람 참조도 만들지 않는다

        flashService.setPlace(1L, 10L, new SetPlaceRequest(
                " 스타벅스 강남R점 ", 37.49, 127.02, "35026031", "서울 강남구"));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("setMeetAt: 처음 정함 → 이전 값 없음, 바꿈 → 이전 값 포함, 같은 시간 → 알리지 않음")
    void setMeetAtNotifications() {
        User sender = user(1L, "보낸이");
        Flash flash = flash(10L, sender);
        MeetupRoom room = room(20L, flash);
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room));
        givenJoined(flash, user(2L, "합류자"));
        given(userRepository.getReferenceById(1L)).willReturn(sender);
        LocalDateTime seven = LocalDateTime.now().plusHours(2).withSecond(0).withNano(0);
        LocalDateTime eight = seven.plusHours(1);

        flashService.setMeetAt(1L, 10L, new SetMeetAtRequest(seven));
        flashService.setMeetAt(1L, 10L, new SetMeetAtRequest(eight));
        flashService.setMeetAt(1L, 10L, new SetMeetAtRequest(eight)); // 같은 값 — 조용히

        ArgumentCaptor<FlashBroadcastEvent> captor = ArgumentCaptor.forClass(FlashBroadcastEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        FlashPushEvent first = captor.getAllValues().get(0).payload();
        FlashPushEvent change = captor.getAllValues().get(1).payload();
        assertThat(first.type()).isEqualTo(FlashPushType.MEET_AT_UPDATED);
        assertThat(first.meetAtBefore()).isNull();
        assertThat(first.meetAtAfter()).isEqualTo(seven);
        assertThat(change.meetAtBefore()).isEqualTo(seven);
        assertThat(change.meetAtAfter()).isEqualTo(eight);
        assertThat(captor.getAllValues().get(1).targetUserIds()).containsExactly(2L);
    }

    @Test
    @DisplayName("setMeetAt: 혼자 있는 방이면 알릴 사람이 없다")
    void setMeetAtAloneIsSilent() {
        User sender = user(1L, "보낸이");
        Flash flash = flash(10L, sender);
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room(20L, flash)));
        given(flashRecipientRepository.findByFlashIdAndStatus(10L, RecipientStatus.JOINED)).willReturn(List.of());
        given(userRepository.getReferenceById(1L)).willReturn(sender);

        flashService.setMeetAt(1L, 10L, new SetMeetAtRequest(LocalDateTime.now().plusHours(1)));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("setPlace: 처음 정함은 changed=false, 바꾸면 true — 방의 '정했어요/바꿨어요'가 알림과 같은 말을 한다")
    void placeChangedFlag() {
        User sender = user(1L, "보낸이");
        User joiner = user(2L, "합류자");
        Flash flash = flash(10L, sender);
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room(20L, flash)));
        FlashRecipient joined = givenJoined(flash, joiner);
        given(flashRecipientRepository.findByFlashIdAndUserId(10L, 2L)).willReturn(Optional.of(joined));
        given(userRepository.getReferenceById(1L)).willReturn(sender);
        given(userRepository.getReferenceById(2L)).willReturn(joiner);

        MeetupRoomResponse first = flashService.setPlace(1L, 10L, new SetPlaceRequest("홍대입구역"));
        assertThat(first.placeChanged()).isFalse();

        MeetupRoomResponse changed = flashService.setPlace(2L, 10L, new SetPlaceRequest("합정역"));
        assertThat(changed.placeChanged()).isTrue();
        assertThat(changed.placeSetByNickname()).isEqualTo("합류자");
    }

    @Test
    @DisplayName("같은 값을 다시 저장하면 아무것도 안 바뀐다 — 정한 사람이 덮이지 않고 '바꿨어요'도 안 된다")
    void sameValueIsNoOp() {
        User sender = user(1L, "보낸이");
        User joiner = user(2L, "합류자");
        Flash flash = flash(10L, sender);
        MeetupRoom room = room(20L, flash);
        LocalDateTime at = LocalDateTime.now().plusHours(2).withSecond(0).withNano(0);
        room.setPlace("홍대입구역", null, null, null, null, sender);
        room.setMeetAt(at, sender);
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room));
        FlashRecipient joined = givenJoined(flash, joiner);
        given(flashRecipientRepository.findByFlashIdAndUserId(10L, 2L)).willReturn(Optional.of(joined));

        MeetupRoomResponse place = flashService.setPlace(2L, 10L, new SetPlaceRequest("홍대입구역"));
        MeetupRoomResponse time = flashService.setMeetAt(2L, 10L, new SetMeetAtRequest(at));

        assertThat(place.placeSetByNickname()).isEqualTo("보낸이");
        assertThat(place.placeChanged()).isFalse();
        assertThat(time.meetAtSetByNickname()).isEqualTo("보낸이");
        assertThat(time.meetAtChanged()).isFalse();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("장소·시간·합류 알림에 모임 종류(activity)가 실린다 — 알림만 보고 어느 모임인지 알게")
    void eventsCarryActivity() {
        User sender = user(1L, "보낸이");
        Flash flash = flash(10L, sender);
        given(meetupRoomRepository.findByFlashId(10L)).willReturn(Optional.of(room(20L, flash)));
        givenJoined(flash, user(2L, "합류자"));
        given(userRepository.getReferenceById(1L)).willReturn(sender);

        flashService.setPlace(1L, 10L, new SetPlaceRequest("홍대입구역"));

        assertThat(publishedEvent().payload().activity()).isEqualTo(Activity.MEAL);
    }

    private FlashRecipient givenJoined(Flash flash, User member) {
        FlashRecipient r = FlashRecipient.builder().flash(flash).user(member).build();
        r.join();
        given(flashRecipientRepository.findByFlashIdAndStatus(flash.getId(), RecipientStatus.JOINED))
                .willReturn(List.of(r));
        return r;
    }

    private FlashBroadcastEvent publishedEvent() {
        ArgumentCaptor<FlashBroadcastEvent> captor = ArgumentCaptor.forClass(FlashBroadcastEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
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

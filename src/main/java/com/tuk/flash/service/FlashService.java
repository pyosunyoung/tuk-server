package com.tuk.flash.service;

import com.tuk.flash.domain.Flash;
import com.tuk.flash.domain.FlashRecipient;
import com.tuk.flash.domain.FlashRecipient.RecipientStatus;
import com.tuk.flash.domain.MeetupRoom;
import com.tuk.flash.dto.FlashResponse;
import com.tuk.flash.dto.MeetupRoomResponse;
import com.tuk.flash.dto.MemberResponse;
import com.tuk.flash.dto.ReceivedFlashResponse;
import com.tuk.flash.dto.FlashPushEvent;
import com.tuk.flash.dto.SendFlashRequest;
import com.tuk.flash.dto.SentFlashResponse;
import com.tuk.flash.domain.Flash.FlashStatus;
import com.tuk.flash.dto.SetPlaceRequest;
import com.tuk.flash.repository.FlashRecipientRepository;
import com.tuk.flash.repository.FlashRepository;
import com.tuk.flash.repository.MeetupRoomRepository;
import com.tuk.flash.ws.FlashBroadcastEvent;
import com.tuk.friendship.repository.FriendshipRepository;
import com.tuk.global.exception.BadRequestException;
import com.tuk.global.exception.ForbiddenException;
import com.tuk.global.exception.NotFoundException;
import com.tuk.user.domain.User;
import com.tuk.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class FlashService {

    private static final int DEFAULT_DURATION_MINUTES = 60;

    private final FlashRepository flashRepository;
    private final FlashRecipientRepository flashRecipientRepository;
    private final MeetupRoomRepository meetupRoomRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public FlashService(FlashRepository flashRepository,
                        FlashRecipientRepository flashRecipientRepository,
                        MeetupRoomRepository meetupRoomRepository,
                        FriendshipRepository friendshipRepository,
                        UserRepository userRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.flashRepository = flashRepository;
        this.flashRecipientRepository = flashRecipientRepository;
        this.meetupRoomRepository = meetupRoomRepository;
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 번개 발송. 대상은 서버에서 절친과 교집합해 재검증한다(원칙 4).
     * Flash + FlashRecipient(PENDING) + MeetupRoom(1:1)을 함께 만든다.
     */
    @Transactional
    public FlashResponse sendFlash(Long senderId, SendFlashRequest request) {
        LocalDateTime now = LocalDateTime.now();
        if (request.scheduledAt() != null && request.scheduledAt().isBefore(now)) {
            throw new BadRequestException("예약 시각은 현재보다 미래여야 합니다.");
        }

        List<Long> targets = resolveTargets(senderId, request.targetUserIds());
        if (targets.isEmpty()) {
            throw new BadRequestException("보낼 절친 대상이 없습니다.");
        }

        int durationMinutes = request.durationMinutes() != null ? request.durationMinutes() : DEFAULT_DURATION_MINUTES;
        LocalDateTime base = request.scheduledAt() != null ? request.scheduledAt() : now;
        LocalDateTime expiresAt = base.plusMinutes(durationMinutes);

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 사용자입니다."));

        Flash flash = flashRepository.save(Flash.builder()
                .sender(sender)
                .activity(request.activity())
                .scheduledAt(request.scheduledAt())
                .message(request.message())
                .expiresAt(expiresAt)
                .build());

        for (Long targetId : targets) {
            flashRecipientRepository.save(FlashRecipient.builder()
                    .flash(flash)
                    .user(userRepository.getReferenceById(targetId))
                    .build());
        }

        MeetupRoom room = meetupRoomRepository.save(MeetupRoom.builder()
                .flash(flash)
                .expiresAt(expiresAt)
                .build());

        // 커밋 이후 접속 중인 수신자에게 "새 번개" 전파 (payload 는 트랜잭션 안에서 완성)
        FlashPushEvent payload = FlashPushEvent.newFlash(flash.getId(), sender.getId(),
                sender.getNickname(), sender.getProfileImage(),
                flash.getActivity(), flash.getMessage(), flash.getScheduledAt(), flash.getExpiresAt());
        eventPublisher.publishEvent(new FlashBroadcastEvent(payload, targets));

        return new FlashResponse(flash.getId(), room.getId(), flash.getActivity(),
                flash.getScheduledAt(), flash.getExpiresAt(), targets.size());
    }

    /** 클라가 보낸 대상을 내 ACCEPTED 절친과 교집합. 대상 미지정이면 절친 전체. */
    private List<Long> resolveTargets(Long senderId, List<Long> requested) {
        List<Long> friendIds = friendshipRepository.findAcceptedFriendIds(senderId);
        if (requested == null || requested.isEmpty()) {
            return friendIds;
        }
        Set<Long> friendSet = new HashSet<>(friendIds);
        return requested.stream().filter(friendSet::contains).distinct().toList();
    }

    /** 내가 받은 활성 번개(ACTIVE + 만료 전). PENDING/JOINED 모두 포함. */
    @Transactional(readOnly = true)
    public List<ReceivedFlashResponse> getReceivedFlashes(Long userId) {
        return flashRecipientRepository.findActiveReceived(userId, LocalDateTime.now()).stream()
                .map(ReceivedFlashResponse::from)
                .toList();
    }

    /**
     * 내가 띄운 활성 번개(ACTIVE + 만료 전 + 이미 시작됨). 홈 "내가 띄운 번개" 배너용.
     * 예약이 아직 도달 안 한 번개는 방이 열리기 전이라 제외한다.
     */
    @Transactional(readOnly = true)
    public List<SentFlashResponse> getSentFlashes(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return flashRepository
                .findBySenderIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(userId, FlashStatus.ACTIVE, now)
                .stream()
                .filter(flash -> flash.getScheduledAt() == null || !flash.getScheduledAt().isAfter(now))
                .map(flash -> {
                    List<MemberResponse> joined = flashRecipientRepository
                            .findByFlashIdAndStatus(flash.getId(), RecipientStatus.JOINED).stream()
                            .map(r -> new MemberResponse(r.getUser().getId(), r.getUser().getNickname(),
                                    r.getUser().getProfileImage(), false))
                            .toList();
                    return new SentFlashResponse(flash.getId(), flash.getActivity(), flash.getMessage(),
                            flash.getScheduledAt(), flash.getExpiresAt(), joined);
                })
                .toList();
    }

    /**
     * 번개 합류. 초대받은(FlashRecipient 존재) 사람만 가능. 멱등 — 이미 JOINED 면 그대로 방(room)만 반환(원칙 3).
     */
    @Transactional
    public MeetupRoomResponse join(Long userId, Long flashId) {
        FlashRecipient recipient = flashRecipientRepository.findByFlashIdAndUserId(flashId, userId)
                .orElseThrow(() -> new ForbiddenException("초대받지 않은 번개입니다."));

        if (!recipient.getFlash().isActive()) {
            throw new BadRequestException("이미 종료되었거나 만료된 번개입니다.");
        }
        boolean firstJoin = !recipient.isJoined();
        if (firstJoin) {
            recipient.join();
        }

        MeetupRoomResponse room = buildRoomResponse(requireRoom(flashId));
        if (firstJoin) {
            // 최초 합류일 때만, 커밋 이후 나머지 멤버에게 "X 합류" 전파(멱등 재호출은 조용히)
            publishMemberJoined(userId, flashId, room);
        }
        return room;
    }

    private void publishMemberJoined(Long joinerId, Long flashId, MeetupRoomResponse room) {
        MemberResponse joiner = room.members().stream()
                .filter(m -> m.userId().equals(joinerId))
                .findFirst()
                .orElse(null);
        if (joiner == null) {
            return;
        }
        List<Long> notifyTargets = room.members().stream()
                .map(MemberResponse::userId)
                .filter(id -> !id.equals(joinerId))
                .toList();
        if (notifyTargets.isEmpty()) {
            return;
        }
        FlashPushEvent payload = FlashPushEvent.memberJoined(
                flashId, joiner.userId(), joiner.nickname(), joiner.profileImage());
        eventPublisher.publishEvent(new FlashBroadcastEvent(payload, notifyTargets));
    }

    @Transactional(readOnly = true)
    public MeetupRoomResponse getRoom(Long userId, Long flashId) {
        MeetupRoom room = requireRoom(flashId);
        checkMember(userId, room.getFlash());
        return buildRoomResponse(room);
    }

    /** 모임방 장소 설정. 멤버만 가능. */
    @Transactional
    public MeetupRoomResponse setPlace(Long userId, Long flashId, SetPlaceRequest request) {
        MeetupRoom room = requireRoom(flashId);
        checkMember(userId, room.getFlash());
        // 프록시라 select 없이 FK 만 채운다. 응답에 쓸 닉네임은 members 에서 찾는다.
        room.setPlace(request.placeName().strip(), request.placeLat(), request.placeLng(),
                request.kakaoPlaceId(), request.placeAddress(),
                userRepository.getReferenceById(userId));
        return buildRoomResponse(room);
    }

    private MeetupRoom requireRoom(Long flashId) {
        return meetupRoomRepository.findByFlashId(flashId)
                .orElseThrow(() -> new NotFoundException("모임방이 존재하지 않습니다."));
    }

    /** 멤버 = 보낸 사람 또는 JOINED 인 수신자. */
    private void checkMember(Long userId, Flash flash) {
        if (flash.getSender().getId().equals(userId)) {
            return;
        }
        boolean joined = flashRecipientRepository.findByFlashIdAndUserId(flash.getId(), userId)
                .map(FlashRecipient::isJoined)
                .orElse(false);
        if (!joined) {
            throw new ForbiddenException("모임방 멤버가 아닙니다.");
        }
    }

    private MeetupRoomResponse buildRoomResponse(MeetupRoom room) {
        Flash flash = room.getFlash();
        User sender = flash.getSender();

        List<MemberResponse> members = new ArrayList<>();
        members.add(new MemberResponse(sender.getId(), sender.getNickname(), sender.getProfileImage(), true)); //보낸사람, 멤버에 방장 추가
        flashRecipientRepository.findByFlashIdAndStatus(flash.getId(), RecipientStatus.JOINED)
                .forEach(recipient -> {
                    User member = recipient.getUser();
                    members.add(new MemberResponse(member.getId(), member.getNickname(), member.getProfileImage(), false));
                }); // 수신자들 멤버 추가

        // 장소를 정한 사람. 항상 멤버이므로 members 에서 닉네임을 찾는다(추가 조회 없음).
        Long placeSetById = room.getPlaceSetBy() != null ? room.getPlaceSetBy().getId() : null;
        String placeSetByNickname = members.stream()
                .filter(m -> m.userId().equals(placeSetById))
                .map(MemberResponse::nickname)
                .findFirst()
                .orElse(null);

        return new MeetupRoomResponse(
                room.getId(),
                flash.getId(),
                flash.getActivity(),
                room.getPlaceName(),
                room.getPlaceLat(),
                room.getPlaceLng(),
                room.getPlaceKakaoId(),
                room.getPlaceAddress(),
                placeSetById,
                placeSetByNickname,
                room.getStatus().name(),
                members);
    }
}

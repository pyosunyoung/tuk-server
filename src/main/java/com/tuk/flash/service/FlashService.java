package com.tuk.flash.service;

import com.tuk.flash.domain.Flash;
import com.tuk.flash.domain.FlashRecipient;
import com.tuk.flash.domain.FlashRecipient.RecipientStatus;
import com.tuk.flash.domain.MeetupLifecycle;
import com.tuk.flash.domain.MeetupRoom;
import com.tuk.flash.dto.FlashResponse;
import com.tuk.flash.dto.MeetupRoomResponse;
import com.tuk.flash.dto.SetMeetAtRequest;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

@Service
public class FlashService {

    private static final int DEFAULT_DURATION_MINUTES = 60;

    /** 툭은 즉흥 만남이라 약속 시간이 하루를 넘지 않는다(오타 방어). */
    private static final int MEET_AT_MAX_AHEAD_HOURS = 24;

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

    /** 내가 받은 툭. PENDING/JOINED 모두 포함(합류 마감 전이거나 약속 시간이 남은 것). */
    @Transactional(readOnly = true)
    public List<ReceivedFlashResponse> getReceivedFlashes(Long userId) {
        List<FlashRecipient> received = flashRecipientRepository.findActiveReceived(userId, LocalDateTime.now());
        Map<Long, LocalDateTime> meetAtByFlashId =
                meetAtByFlashId(received.stream().map(r -> r.getFlash().getId()).toList());
        return received.stream()
                .map(r -> ReceivedFlashResponse.from(r, meetAtByFlashId.get(r.getFlash().getId())))
                .toList();
    }

    /** flashId → 약속 시간. 목록에서 방마다 조회하면 N+1 이므로 한 번에 가져온다. */
    private Map<Long, LocalDateTime> meetAtByFlashId(List<Long> flashIds) {
        if (flashIds.isEmpty()) return Map.of();
        Map<Long, LocalDateTime> map = new HashMap<>();
        for (MeetupRoom room : meetupRoomRepository.findByFlashIdIn(flashIds)) {
            if (room.getMeetAt() != null) {
                map.put(room.getFlash().getId(), room.getMeetAt());
            }
        }
        return map;
    }

    /**
     * 홈 "진행 중인 모임" 에 보일, 내가 띄운 툭.
     * 합류 마감이 지나도 성사된 모임은 계속 보인다(어디서 몇 시인지 다시 봐야 하므로 — MeetupLifecycle).
     * 예약이 아직 도달 안 한 툭은 방이 열리기 전이라 제외한다.
     */
    @Transactional(readOnly = true)
    public List<SentFlashResponse> getSentFlashes(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<Flash> flashes = flashRepository.findSentStillRelevant(userId, FlashStatus.ACTIVE, now,
                        MeetupLifecycle.unscheduledCutoff(now)).stream()
                .filter(flash -> flash.getScheduledAt() == null || !flash.getScheduledAt().isAfter(now))
                .toList();
        List<Long> flashIds = flashes.stream().map(Flash::getId).toList();
        Map<Long, LocalDateTime> meetAtByFlashId = meetAtByFlashId(flashIds);

        // 수신자를 한 번에 가져와 flash 별로 나눈다(예전엔 flash 마다 조회해 N+1 이었다)
        Map<Long, List<FlashRecipient>> recipientsByFlashId = new HashMap<>();
        if (!flashIds.isEmpty()) {
            for (FlashRecipient r : flashRecipientRepository.findByFlashIdIn(flashIds)) {
                recipientsByFlashId.computeIfAbsent(r.getFlash().getId(), k -> new ArrayList<>()).add(r);
            }
        }

        return flashes.stream()
                .map(flash -> {
                    List<FlashRecipient> recipients =
                            recipientsByFlashId.getOrDefault(flash.getId(), List.of());
                    List<Long> recipientIds = recipients.stream()
                            .map(r -> r.getUser().getId())
                            .toList();
                    List<MemberResponse> joined = recipients.stream()
                            .filter(r -> r.getStatus() == RecipientStatus.JOINED)
                            .map(r -> new MemberResponse(r.getUser().getId(), r.getUser().getNickname(),
                                    r.getUser().getProfileImage(), false))
                            .toList();
                    LocalDateTime meetAt = meetAtByFlashId.get(flash.getId());
                    return new SentFlashResponse(flash.getId(), flash.getActivity(), flash.getMessage(),
                            flash.getScheduledAt(), flash.getExpiresAt(), meetAt,
                            MeetupLifecycle.visibleUntil(flash.getExpiresAt(), meetAt, !joined.isEmpty()),
                            recipientIds, joined);
                })
                .toList();
    }

    /**
     * 번개 합류. 초대받은(FlashRecipient 존재) 사람만 가능. 멱등 — 이미 JOINED 면 그대로 방(room)만 반환(원칙 3).
     */
    @Transactional
    public MeetupRoomResponse join(Long userId, Long flashId) {
        FlashRecipient recipient = flashRecipientRepository.findByFlashIdAndUserId(flashId, userId)
                .orElseThrow(() -> new ForbiddenException("초대받지 않은 툭이에요."));

        if (!recipient.getFlash().isActive()) {
            throw new BadRequestException("합류 시간이 끝난 툭이에요.");
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
        publishToOtherMembers(joinerId, room, joiner -> FlashPushEvent.memberJoined(
                flashId, joiner.userId(), joiner.nickname(), joiner.profileImage()));
    }

    /**
     * 커밋 이후, 행동한 사람을 뺀 모임방 멤버에게 전파한다(접속 중이면 WebSocket, 아니면 FCM).
     * 행동한 사람은 항상 멤버라 members 에서 닉네임을 찾는다(추가 조회 없음).
     */
    private void publishToOtherMembers(Long actorId, MeetupRoomResponse room,
                                       Function<MemberResponse, FlashPushEvent> payloadFor) {
        MemberResponse actor = room.members().stream()
                .filter(m -> m.userId().equals(actorId))
                .findFirst()
                .orElse(null);
        if (actor == null) {
            return;
        }
        List<Long> notifyTargets = room.members().stream()
                .map(MemberResponse::userId)
                .filter(id -> !id.equals(actorId))
                .toList();
        if (notifyTargets.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(new FlashBroadcastEvent(payloadFor.apply(actor), notifyTargets));
    }

    @Transactional(readOnly = true)
    public MeetupRoomResponse getRoom(Long userId, Long flashId) {
        MeetupRoom room = requireRoom(flashId);
        checkMember(userId, room.getFlash());
        return buildRoomResponse(room);
    }

    /**
     * 모임방 장소 설정. 멤버만 가능.
     * 정하거나 바꾸면 나머지 멤버에게 알린다. 같은 장소를 다시 고른 건 알리지 않는다.
     */
    @Transactional
    public MeetupRoomResponse setPlace(Long userId, Long flashId, SetPlaceRequest request) {
        MeetupRoom room = requireRoom(flashId);
        checkMember(userId, room.getFlash());
        String before = room.getPlaceName();
        String beforeKakaoId = room.getPlaceKakaoId();
        String after = request.placeName().strip();
        // 프록시라 select 없이 FK 만 채운다. 응답에 쓸 닉네임은 members 에서 찾는다.
        room.setPlace(after, request.placeLat(), request.placeLng(),
                request.kakaoPlaceId(), request.placeAddress(),
                userRepository.getReferenceById(userId));

        MeetupRoomResponse res = buildRoomResponse(room);
        boolean unchanged = after.equals(before) && Objects.equals(beforeKakaoId, request.kakaoPlaceId());
        if (!unchanged) {
            publishToOtherMembers(userId, res, setter -> FlashPushEvent.placeUpdated(
                    flashId, setter.userId(), setter.nickname(), setter.profileImage(), before, after));
        }
        return res;
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

    /**
     * 모임방 약속 시간 설정. 멤버라면 아무나 정하고 아무나 바꾼다(장소와 같은 규칙).
     *
     * 만료(expiresAt)는 건드리지 않는다. 약속을 늘릴 때마다 만료가 따라 늘어나면
     * "시간 지나면 알아서 꺼져요"라는 약속이 무의미해진다. 만료는 '모집 마감'이고
     * 약속 시간은 '만나는 시각' — 서로 다른 일을 한다.
     */
    @Transactional
    public MeetupRoomResponse setMeetAt(Long userId, Long flashId, SetMeetAtRequest request) {
        MeetupRoom room = requireRoom(flashId);
        checkMember(userId, room.getFlash());

        LocalDateTime meetAt = request.meetAt();
        LocalDateTime now = LocalDateTime.now();
        // 이미 지난 시각은 약속이 될 수 없다. 다만 "8시"를 8시 정각에 누르는 경우가 있어 조금 봐준다.
        if (meetAt.isBefore(now.minusHours(1))) {
            throw new BadRequestException("이미 지난 시간이에요.");
        }
        // 툭은 즉흥 만남이라 하루를 넘기지 않는다(오타·잘못된 입력 방어).
        if (meetAt.isAfter(now.plusHours(MEET_AT_MAX_AHEAD_HOURS))) {
            throw new BadRequestException("하루 안의 시간만 정할 수 있어요.");
        }

        LocalDateTime before = room.getMeetAt();
        room.setMeetAt(meetAt, userRepository.getReferenceById(userId));

        MeetupRoomResponse res = buildRoomResponse(room);
        if (!meetAt.equals(before)) {
            publishToOtherMembers(userId, res, setter -> FlashPushEvent.meetAtUpdated(
                    flashId, setter.userId(), setter.nickname(), setter.profileImage(), before, meetAt));
        }
        return res;
    }

    /** 정한 사람은 항상 멤버이므로 members 에서 닉네임을 찾는다(추가 조회 없음). */
    private static String nicknameOf(List<MemberResponse> members, Long userId) {
        if (userId == null) return null;
        return members.stream()
                .filter(m -> m.userId().equals(userId))
                .map(MemberResponse::nickname)
                .findFirst()
                .orElse(null);
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
        String placeSetByNickname = nicknameOf(members, placeSetById);

        Long meetAtSetById = room.getMeetAtSetBy() != null ? room.getMeetAtSetBy().getId() : null;
        String meetAtSetByNickname = nicknameOf(members, meetAtSetById);

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
                room.getMeetAt(),
                meetAtSetById,
                meetAtSetByNickname,
                room.getStatus().name(),
                room.getExpiresAt(),
                members);
    }
}

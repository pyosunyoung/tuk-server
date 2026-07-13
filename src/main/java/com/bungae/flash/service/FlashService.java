package com.bungae.flash.service;

import com.bungae.flash.domain.Flash;
import com.bungae.flash.domain.FlashRecipient;
import com.bungae.flash.domain.FlashRecipient.RecipientStatus;
import com.bungae.flash.domain.MeetupRoom;
import com.bungae.flash.dto.FlashResponse;
import com.bungae.flash.dto.MeetupRoomResponse;
import com.bungae.flash.dto.MemberResponse;
import com.bungae.flash.dto.ReceivedFlashResponse;
import com.bungae.flash.dto.SendFlashRequest;
import com.bungae.flash.dto.SetPlaceRequest;
import com.bungae.flash.repository.FlashRecipientRepository;
import com.bungae.flash.repository.FlashRepository;
import com.bungae.flash.repository.MeetupRoomRepository;
import com.bungae.friendship.repository.FriendshipRepository;
import com.bungae.global.exception.BadRequestException;
import com.bungae.global.exception.ForbiddenException;
import com.bungae.global.exception.NotFoundException;
import com.bungae.user.domain.User;
import com.bungae.user.repository.UserRepository;
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

    public FlashService(FlashRepository flashRepository,
                        FlashRecipientRepository flashRecipientRepository,
                        MeetupRoomRepository meetupRoomRepository,
                        FriendshipRepository friendshipRepository,
                        UserRepository userRepository) {
        this.flashRepository = flashRepository;
        this.flashRecipientRepository = flashRecipientRepository;
        this.meetupRoomRepository = meetupRoomRepository;
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
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

        Flash flash = flashRepository.save(Flash.builder()
                .sender(userRepository.getReferenceById(senderId))
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
     * 번개 합류. 초대받은(FlashRecipient 존재) 사람만 가능. 멱등 — 이미 JOINED 면 그대로 방(room)만 반환(원칙 3).
     */
    @Transactional
    public MeetupRoomResponse join(Long userId, Long flashId) {
        FlashRecipient recipient = flashRecipientRepository.findByFlashIdAndUserId(flashId, userId)
                .orElseThrow(() -> new ForbiddenException("초대받지 않은 번개입니다."));

        if (!recipient.getFlash().isActive()) {
            throw new BadRequestException("이미 종료되었거나 만료된 번개입니다.");
        }
        if (!recipient.isJoined()) {
            recipient.join();
        }
        return buildRoomResponse(requireRoom(flashId));
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
        room.setPlace(request.placeName(), request.placeLat(), request.placeLng());
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
        members.add(new MemberResponse(sender.getId(), sender.getNickname(), sender.getProfileImage(), true));
        flashRecipientRepository.findByFlashIdAndStatus(flash.getId(), RecipientStatus.JOINED)
                .forEach(recipient -> {
                    User member = recipient.getUser();
                    members.add(new MemberResponse(member.getId(), member.getNickname(), member.getProfileImage(), false));
                });

        return new MeetupRoomResponse(
                room.getId(),
                flash.getId(),
                flash.getActivity(),
                room.getPlaceName(),
                room.getPlaceLat(),
                room.getPlaceLng(),
                room.getStatus().name(),
                members);
    }
}

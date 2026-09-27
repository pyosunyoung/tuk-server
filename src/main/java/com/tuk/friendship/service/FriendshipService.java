package com.tuk.friendship.service;

import com.tuk.friendship.domain.Friendship;
import com.tuk.friendship.domain.Friendship.FriendshipStatus;
import com.tuk.friendship.dto.FriendRequestResult;
import com.tuk.friendship.dto.FriendResponse;
import com.tuk.friendship.dto.InviteCodeResponse;
import com.tuk.friendship.dto.ReceivedRequestResponse;
import com.tuk.friendship.event.FriendshipEvent;
import com.tuk.friendship.repository.FriendshipRepository;
import com.tuk.global.exception.BadRequestException;
import com.tuk.global.exception.ForbiddenException;
import com.tuk.global.exception.NotFoundException;
import com.tuk.user.domain.User;
import com.tuk.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

@Service
public class FriendshipService {

    // 혼동되는 문자(0/O/1/I/L) 제외한 초대코드 알파벳.
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final int CODE_MAX_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public FriendshipService(FriendshipRepository friendshipRepository, UserRepository userRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 친구 신청. 방향 무관 기존 관계를 먼저 확인:
     *  - 이미 ACCEPTED → 이미 친구
     *  - 상대가 나에게 이미 신청(PENDING) → 내 신청은 곧 수락(맞신청) → ACCEPTED
     *  - 내가 이미 신청(PENDING) → 중복
     *  - 관계 없음 → 새 PENDING 생성
     */
    @Transactional
    public FriendRequestResult sendRequest(Long requesterId, Long addresseeId) {
        if (requesterId.equals(addresseeId)) {
            throw new BadRequestException("자기 자신에게는 친구 신청을 할 수 없습니다.");
        }

        User addressee = userRepository.findById(addresseeId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 사용자입니다."));

        Optional<Friendship> existing = friendshipRepository.findBetween(requesterId, addresseeId);
        if (existing.isPresent()) {
            Friendship friendship = existing.get();
            if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
                throw new BadRequestException("이미 친구입니다.");
            }
            // PENDING 상태: 그 신청의 수신자가 나라면 = 상대가 먼저 보낸 것 → 맞신청으로 수락
            if (friendship.getAddressee().getId().equals(requesterId)) {
                friendship.accept();
                // 먼저 신청했던 쪽에게는 수락된 것으로 알린다(신청이 새로 온 게 아니다)
                eventPublisher.publishEvent(FriendshipEvent.accepted(
                        friendship.getRequester().getId(), nicknameOf(requesterId)));
                return FriendRequestResult.from(friendship);
            }
            throw new BadRequestException("이미 친구 신청을 보냈습니다.");
        }

        User requester = userRepository.getReferenceById(requesterId);
        try {
            // saveAndFlush: uq_friend_pair 위반을 여기서(트랜잭션 커밋 전) 즉시 감지하기 위함.
            // 두 사람이 동시에 서로 신청해 findBetween 을 둘 다 통과한 경합에서, 진 쪽을 500 대신 400 으로.
            Friendship saved = friendshipRepository.saveAndFlush(
                    Friendship.builder().requester(requester).addressee(addressee).build());
            eventPublisher.publishEvent(FriendshipEvent.request(addresseeId, nicknameOf(requesterId)));
            return FriendRequestResult.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new BadRequestException("이미 친구 신청이 진행 중이거나 친구입니다.");
        }
    }

    /** 친구 신청 수락. 수신자 본인만 가능. */
    @Transactional
    public void accept(Long userId, Long friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 친구 신청입니다."));

        if (!friendship.getAddressee().getId().equals(userId)) {
            throw new ForbiddenException("수락 권한이 없습니다.");
        }
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            throw new BadRequestException("이미 수락된 신청입니다.");
        }
        friendship.accept();
        eventPublisher.publishEvent(FriendshipEvent.accepted(
                friendship.getRequester().getId(), friendship.getAddressee().getNickname()));
    }

    /**
     * 푸시 문구에 쓸 닉네임. requester 는 getReferenceById 프록시라 바로 getNickname() 하면
     * 추가 쿼리가 숨어버린다 — 여기서 명시적으로 읽는다(커밋 후 lazy 로딩도 피한다).
     */
    private String nicknameOf(Long userId) {
        return userRepository.findById(userId)
                .map(User::getNickname)
                .orElse("친구");
    }

    /** 내가 받은 수락 대기 신청 목록. */
    @Transactional(readOnly = true)
    public List<ReceivedRequestResponse> getReceivedRequests(Long userId) {
        return friendshipRepository.findPendingReceived(userId).stream()
                .map(ReceivedRequestResponse::from)
                .toList();
    }

    /** 내 친구 목록(ACCEPTED, 방향 무관). */
    @Transactional(readOnly = true)
    public List<FriendResponse> getFriends(Long userId) {
        List<Long> friendIds = friendshipRepository.findAcceptedFriendIds(userId);
        return userRepository.findAllById(friendIds).stream()
                .map(FriendResponse::from)
                .toList();
    }

    /** 내 초대코드 조회(없으면 지연 생성). */
    @Transactional
    public InviteCodeResponse getOrCreateInviteCode(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 사용자입니다."));
        if (user.getInviteCode() == null) {
            user.assignInviteCode(generateUniqueCode());
        }
        return new InviteCodeResponse(user.getInviteCode());
    }

    /**
     * 초대코드로 친구 맺기. 코드의 주인에게 친구 신청을 보낸다(맞신청이면 즉시 ACCEPTED).
     * 이후 로직은 {@link #sendRequest(Long, Long)} 와 동일한 규칙을 따른다.
     */
    @Transactional
    public FriendRequestResult redeemInviteCode(Long userId, String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        if (code.isEmpty()) {
            throw new BadRequestException("초대코드를 입력해주세요.");
        }
        User target = userRepository.findByInviteCode(code)
                .orElseThrow(() -> new NotFoundException("유효하지 않은 초대코드입니다."));
        if (target.getId().equals(userId)) {
            throw new BadRequestException("본인의 초대코드입니다.");
        }
        return sendRequest(userId, target.getId());
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < CODE_MAX_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (!userRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("초대코드 생성에 실패했습니다. 다시 시도해주세요.");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}

package com.bungae.friendship.service;

import com.bungae.friendship.domain.Friendship;
import com.bungae.friendship.domain.Friendship.FriendshipStatus;
import com.bungae.friendship.dto.FriendRequestResult;
import com.bungae.friendship.dto.FriendResponse;
import com.bungae.friendship.dto.ReceivedRequestResponse;
import com.bungae.friendship.repository.FriendshipRepository;
import com.bungae.global.exception.BadRequestException;
import com.bungae.global.exception.ForbiddenException;
import com.bungae.global.exception.NotFoundException;
import com.bungae.user.domain.User;
import com.bungae.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    public FriendshipService(FriendshipRepository friendshipRepository, UserRepository userRepository) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
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
}

package com.tuk.user.service;

import com.tuk.flash.repository.FlashRecipientRepository;
import com.tuk.flash.repository.FlashRepository;
import com.tuk.flash.repository.MeetupRoomRepository;
import com.tuk.friendship.repository.FriendshipRepository;
import com.tuk.global.auth.token.RefreshTokenStore;
import com.tuk.global.exception.NotFoundException;
import com.tuk.notification.repository.DeviceTokenRepository;
import com.tuk.presence.service.PresenceService;
import com.tuk.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 탈퇴. 내가 만든 것과 나를 가리키는 것을 지운다.
 *
 * <ul>
 *   <li>내가 띄운 툭 → 초대 기록·모임방과 함께 삭제 (합류했던 친구 화면에서도 사라진다)</li>
 *   <li>남의 툭에 받은 초대·합류 → 삭제 (그 모임방 멤버 목록에서 빠진다. "탈퇴한 사용자"로 남기면 이름 없는 유령 멤버가 된다)</li>
 *   <li>남의 모임방에서 내가 장소·시간을 "정한 사람"으로 남은 기록 → 비움 (장소·시간 자체는 그 방의 것이라 남긴다)</li>
 *   <li>친구 관계·신청 → 삭제 (상대 목록에서 조용히 사라진다 — "거절 안 보임"과 같은 결)</li>
 *   <li>기기 토큰, "지금 한가해요", 로그인 유지 토큰 → 삭제</li>
 * </ul>
 *
 * 외래키에 ON DELETE CASCADE 가 없어서 순서가 곧 정합성이다. 자식 → 부모 순으로 지운다.
 * DB 는 한 트랜잭션으로 묶고, Redis("한가해요"·refresh 토큰)는 그 바깥에서 처리한다.
 * "한가해요"는 친구 관계를 지우기 **전에** 꺼야 누구에게 사라졌다고 알릴지 안다.
 *
 * 카카오 연결 끊기는 앱이 카카오 SDK 로 한다(서버엔 사용자의 카카오 토큰이 없다).
 * DB 백업(14일)에는 남는다 — 개인정보 처리방침에 적어 둔 대로.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final FlashRepository flashRepository;
    private final FlashRecipientRepository flashRecipientRepository;
    private final MeetupRoomRepository meetupRoomRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PresenceService presenceService;
    private final RefreshTokenStore refreshTokenStore;
    private final TransactionTemplate tx;

    public AccountDeletionService(UserRepository userRepository,
                                  FriendshipRepository friendshipRepository,
                                  FlashRepository flashRepository,
                                  FlashRecipientRepository flashRecipientRepository,
                                  MeetupRoomRepository meetupRoomRepository,
                                  DeviceTokenRepository deviceTokenRepository,
                                  PresenceService presenceService,
                                  RefreshTokenStore refreshTokenStore,
                                  TransactionTemplate tx) {
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.flashRepository = flashRepository;
        this.flashRecipientRepository = flashRecipientRepository;
        this.meetupRoomRepository = meetupRoomRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.presenceService = presenceService;
        this.refreshTokenStore = refreshTokenStore;
        this.tx = tx;
    }

    public void delete(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("사용자를 찾을 수 없습니다.");
        }

        // 1. 친구 관계가 남아 있을 때 꺼야 친구들 화면에서 바로 사라진다
        presenceService.turnOff(userId);

        // 2. DB — 자식에서 부모 순으로, 한 트랜잭션
        tx.executeWithoutResult(status -> {
            meetupRoomRepository.clearPlaceSetBy(userId);
            meetupRoomRepository.clearMeetAtSetBy(userId);
            flashRecipientRepository.deleteByUserId(userId);
            flashRecipientRepository.deleteByFlashSender(userId);
            meetupRoomRepository.deleteBySender(userId);
            int flashes = flashRepository.deleteBySenderId(userId);
            int friendships = friendshipRepository.deleteAllInvolving(userId);
            deviceTokenRepository.deleteAllByUserId(userId);
            userRepository.deleteById(userId);
            log.info("탈퇴 처리 userId={} flashes={} friendships={}", userId, flashes, friendships);
        });

        // 3. 로그인 유지 토큰 — 남은 access 토큰은 /users/me 가 404 를 주면 앱이 세션을 정리한다
        refreshTokenStore.delete(userId);
    }
}

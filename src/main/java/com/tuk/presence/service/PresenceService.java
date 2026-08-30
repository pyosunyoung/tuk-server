package com.tuk.presence.service;

import com.tuk.friendship.repository.FriendshipRepository;
import com.tuk.global.common.Activity;
import com.tuk.presence.domain.Presence;
import com.tuk.presence.dto.AvailableFriendResponse;
import com.tuk.presence.dto.MyPresenceResponse;
import com.tuk.presence.repository.PresenceRepository;
import com.tuk.presence.ws.PresenceEventBroadcaster;
import com.tuk.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PresenceService {

    private final PresenceRepository presenceRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final PresenceEventBroadcaster broadcaster;

    public PresenceService(PresenceRepository presenceRepository,
                           FriendshipRepository friendshipRepository,
                           UserRepository userRepository,
                           PresenceEventBroadcaster broadcaster) {
        this.presenceRepository = presenceRepository;
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.broadcaster = broadcaster;
    }

    /** 콜 켜기. 기존 상태가 있어도 덮어써서 갱신(활동/TTL 재설정). 접속 중인 절친에게 실시간 전파. */
    public MyPresenceResponse turnOn(Long userId, Activity activity, long durationSeconds) {
        Presence saved = presenceRepository.save(Presence.builder()
                .userId(userId)
                .activity(activity)
                .ttlSeconds(durationSeconds)
                .build());
        broadcaster.broadcastAvailable(userId, activity, saved.getTtlSeconds());
        return MyPresenceResponse.of(saved);
    }

    /** 콜 끄기(수동). 안 꺼도 TTL 로 자동 만료된다. */
    public void turnOff(Long userId) {
        presenceRepository.deleteById(userId);
        broadcaster.broadcastUnavailable(userId);
    }

    public MyPresenceResponse getMyPresence(Long userId) {
        return presenceRepository.findById(userId)
                .map(MyPresenceResponse::of)
                .orElseGet(MyPresenceResponse::off);
    }

    /**
     * 지금 한가한 친구 조회 (헤드라인 쿼리).
     *  1) RDB: 내 ACCEPTED 절친 id (서버측 절친 재검증)
     *  2) Redis: 그중 presence 가 살아있는(=지금 한가한) 사람만 (findAllById)
     *  3) RDB: 표시용 유저 정보 결합
     * 만료된 사람은 Redis 에 없어 자연히 제외된다.
     */
    @Transactional(readOnly = true)
    public List<AvailableFriendResponse> getAvailableFriends(Long userId) {
        List<Long> friendIds = friendshipRepository.findAcceptedFriendIds(userId); // 서로 친구 수락된 친구들의 ID목록(RDB)
        if (friendIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Presence> presenceByUserId = new LinkedHashMap<>();
        presenceRepository.findAllById(friendIds)
                .forEach(p -> presenceByUserId.put(p.getUserId(), p)); // 한가함 상태의 LIST만 가져옴.(Redis)
        if (presenceByUserId.isEmpty()) { // 앱을 켜두지 않았거나, 지정 시간 만료 친구는 Redis에 존재하지 않음
            return List.of();
        }
        // 3 단계: 살아있는 친구들의 구체적인 프로필 정보(닉네임, 사진)를 RDB에서 가져와 결합합니다.
        return userRepository.findAllById(presenceByUserId.keySet()).stream()
                .map(user -> {
                    Presence presence = presenceByUserId.get(user.getId());
                    return new AvailableFriendResponse(
                            user.getId(),
                            user.getNickname(),
                            user.getProfileImage(),
                            presence.getActivity(),
                            presence.getTtlSeconds());
                })
                .toList();
    }
}

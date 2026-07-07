package com.bungae.presence.service;

import com.bungae.friendship.repository.FriendshipRepository;
import com.bungae.global.common.Activity;
import com.bungae.presence.domain.Presence;
import com.bungae.presence.dto.AvailableFriendResponse;
import com.bungae.presence.dto.MyPresenceResponse;
import com.bungae.presence.repository.PresenceRepository;
import com.bungae.user.repository.UserRepository;
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

    public PresenceService(PresenceRepository presenceRepository,
                           FriendshipRepository friendshipRepository,
                           UserRepository userRepository) {
        this.presenceRepository = presenceRepository;
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
    }

    /** 콜 켜기. 기존 상태가 있어도 덮어써서 갱신(활동/TTL 재설정). */
    public MyPresenceResponse turnOn(Long userId, Activity activity, long durationSeconds) {
        Presence saved = presenceRepository.save(Presence.builder()
                .userId(userId)
                .activity(activity)
                .ttlSeconds(durationSeconds)
                .build());
        return MyPresenceResponse.of(saved);
    }

    /** 콜 끄기(수동). 안 꺼도 TTL 로 자동 만료된다. */
    public void turnOff(Long userId) {
        presenceRepository.deleteById(userId);
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
        List<Long> friendIds = friendshipRepository.findAcceptedFriendIds(userId);
        if (friendIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Presence> presenceByUserId = new LinkedHashMap<>();
        presenceRepository.findAllById(friendIds)
                .forEach(p -> presenceByUserId.put(p.getUserId(), p));
        if (presenceByUserId.isEmpty()) {
            return List.of();
        }

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

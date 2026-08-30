package com.tuk.presence.repository;

import com.tuk.presence.domain.Presence;
import org.springframework.data.repository.CrudRepository;

/**
 * Redis 저장소(@RedisHash). 복잡한 조건 쿼리는 불가 — 기본 CRUD 만.
 *
 * "지금 한가한 친구 조회" 2단계:
 *   FriendshipRepository.findAcceptedFriendIds(me) 로 절친 ID 목록을 얻은 뒤
 *   findAllById(friendIds) 를 호출하면, TTL 이 아직 살아있는(=지금 한가한) 사용자만 돌아온다.
 *   (만료된 사용자는 Redis 에서 이미 사라져 결과에 포함되지 않음 → '자동 만료' 원칙)
 */
public interface PresenceRepository extends CrudRepository<Presence, Long> {
}

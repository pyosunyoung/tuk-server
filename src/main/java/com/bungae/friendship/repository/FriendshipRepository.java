package com.bungae.friendship.repository;

import com.bungae.friendship.domain.Friendship;
import com.bungae.friendship.domain.Friendship.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    /** 두 사용자 사이의 관계(방향 무관). 친구 신청 중복 방지·재검증에 사용. */
    @Query("""
            select f from Friendship f
            where (f.requester.id = :aId and f.addressee.id = :bId)
               or (f.requester.id = :bId and f.addressee.id = :aId)
            """)
    Optional<Friendship> findBetween(@Param("aId") Long aId, @Param("bId") Long bId);

    /** 내가 받은 친구 신청(수락 대기). 신청자(requester)까지 fetch. */
    @Query("""
            select f from Friendship f
            join fetch f.requester
            where f.addressee.id = :userId and f.status = :status
            """)
    List<Friendship> findReceivedRequests(@Param("userId") Long userId,
                                          @Param("status") FriendshipStatus status);

    default List<Friendship> findPendingReceived(Long userId) {
        return findReceivedRequests(userId, FriendshipStatus.PENDING);
    }

    /**
     * 절친 ID 목록(방향 무관, ACCEPTED).
     * "지금 한가한 친구 조회"의 1단계 + 번개 대상 서버측 절친 재검증의 기준이 된다.
     */
    @Query("""
            select case when f.requester.id = :userId then f.addressee.id else f.requester.id end
            from Friendship f
            where f.status = :status
              and (f.requester.id = :userId or f.addressee.id = :userId)
            """)
    List<Long> findFriendIds(@Param("userId") Long userId, @Param("status") FriendshipStatus status);

    default List<Long> findAcceptedFriendIds(Long userId) {
        return findFriendIds(userId, FriendshipStatus.ACCEPTED);
    }
}

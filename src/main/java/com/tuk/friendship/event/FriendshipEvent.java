package com.tuk.friendship.event;

/**
 * 친구 관계 변화 알림. 커밋 후에 FCM 으로 나간다([FriendshipPushListener]).
 *
 * 툭(Flash)과 달리 "무시"라는 선택지가 없다 — 거절 버튼 자체가 없고, 신청은 내가 코드를
 * 건넨 사람만 보낼 수 있다. 그래서 알림이 원칙에 어긋나지 않는다. 오히려 없으면
 * 상대가 앱을 안 열었을 뿐인데 거절당한 것처럼 느껴져, "거절 안 보임"의 취지가 깨진다.
 *
 * 다만 **재촉은 하지 않는다** — "N일째 기다려요" 같은 리마인드는 만들지 않는다.
 *
 * payload(닉네임 등)는 트랜잭션 안에서 완성해 담는다. 커밋 후 lazy 로딩을 피하기 위함.
 */
public record FriendshipEvent(
        Type type,
        /** 알림을 받을 사람. */
        Long targetUserId,
        /** 알림을 만든 사람(신청한 사람 / 수락한 사람). */
        String actorNickname
) {
    public enum Type {
        /** 친구 신청이 도착했다 → 받는 사람에게. */
        FRIEND_REQUEST,
        /** 내 신청이 수락됐다 → 신청했던 사람에게. */
        FRIEND_ACCEPTED
    }

    public static FriendshipEvent request(Long targetUserId, String actorNickname) {
        return new FriendshipEvent(Type.FRIEND_REQUEST, targetUserId, actorNickname);
    }

    public static FriendshipEvent accepted(Long targetUserId, String actorNickname) {
        return new FriendshipEvent(Type.FRIEND_ACCEPTED, targetUserId, actorNickname);
    }
}

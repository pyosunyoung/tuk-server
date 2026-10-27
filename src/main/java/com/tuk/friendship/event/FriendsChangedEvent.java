package com.tuk.friendship.event;

/**
 * 친구 관계가 사라졌다(친구 끊기 · 보낸 신청 취소) → 상대 앱이 목록만 조용히 다시 불러오게 하는 신호.
 *
 * 알림이 아니다 — 푸시도 토스트도 없다("따로 알리지 않아요"). 이게 없으면 상대는 앱을 다시 열 때까지
 * 끊은 사람을 친구 목록 · 지금 한가한 친구 · 툭 보낼 대상에서 계속 보게 된다(실사용 피드백 10-25).
 *
 * @param targetUserId 목록을 맞춰야 하는 사람(끊긴 쪽 / 신청을 받았던 쪽)
 */
public record FriendsChangedEvent(Long targetUserId) {
}

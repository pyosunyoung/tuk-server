package com.tuk.flash.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 툭이 홈 목록에 언제까지 보이는가 — 규칙은 여기 한 곳에만 둔다.
 *
 * <ul>
 *   <li>모임이 성사되지 않았으면(합류자 0명 / 나는 아직 합류 전) → 합류 마감까지.</li>
 *   <li>성사됐고 약속 시간이 있으면 → 마감과 약속 시간 중 늦은 쪽까지.</li>
 *   <li>성사됐는데 약속 시간이 없으면 → 마감 후 {@link #UNSCHEDULED_GRACE} 까지.</li>
 * </ul>
 *
 * 합류 마감 ≠ 모임 종료다. 마감 1분 전에 합류했거나 아직 시간을 고민 중인 모임이
 * 마감과 함께 홈에서 사라지면 안 된다. 툭은 즉흥 모임이라 당일이면 충분해서 6시간.
 * 약속을 정해도 expiresAt 은 늘리지 않는다 — 보이는 기간만 따로 계산한다.
 *
 * 목록 쿼리(FlashRepository·FlashRecipientRepository)도 같은 규칙을 JPQL 로 옮겨 쓴다.
 * 응답의 visibleUntil 로 앱에 내려주므로 앱은 이 규칙을 따로 들고 있지 않는다.
 */
public final class MeetupLifecycle {

    public static final Duration UNSCHEDULED_GRACE = Duration.ofHours(6);

    private MeetupLifecycle() {
    }

    public static LocalDateTime visibleUntil(LocalDateTime expiresAt, LocalDateTime meetAt, boolean formed) {
        if (!formed) return expiresAt;
        if (meetAt == null) return expiresAt.plus(UNSCHEDULED_GRACE);
        return meetAt.isAfter(expiresAt) ? meetAt : expiresAt;
    }

    /** 쿼리용: 약속 시간 없는 성사 모임은 expiresAt 이 이 시각보다 뒤면 아직 보인다. */
    public static LocalDateTime unscheduledCutoff(LocalDateTime now) {
        return now.minus(UNSCHEDULED_GRACE);
    }
}

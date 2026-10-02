package com.tuk.notification.service;

import com.tuk.global.common.Activity;

/**
 * 푸시 문구용 활동 이름. 앱의 Activity.label(models/activity.dart)과 같은 말이어야 한다 —
 * 알림은 "카페"인데 앱은 "커피"라고 하면 다른 모임처럼 보인다.
 */
final class ActivityLabel {

    private ActivityLabel() {
    }

    static String of(Activity activity) {
        if (activity == null) return null;
        return switch (activity) {
            case MEAL -> "밥";
            case DRINK -> "술";
            case CAFE -> "카페";
            case WALK -> "산책";
            case MOVIE -> "영화";
            case KARAOKE -> "노래방";
            case PC_ROOM -> "PC방";
            case ANYTHING -> "아무거나";
        };
    }

    /** "밥 · " 처럼 앞에 붙일 말. 활동을 모르면 빈 문자열. */
    static String prefix(Activity activity) {
        String label = of(activity);
        return label == null ? "" : label + " · ";
    }
}

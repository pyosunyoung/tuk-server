package com.tuk.notification.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 푸시 문구용 약속 시간 표기 — "오늘 오후 8시", "내일 오전 11시 30분".
 * 앱의 meetTimeLabel(meet_time_sheet.dart)과 같은 모양이다. 알림과 화면이 다르게 말하면 헷갈린다.
 * 서버 벽시계가 KST 라(TZ=Asia/Seoul) 오늘/내일 판단도 한국 기준이다.
 */
final class MeetTimeLabel {

    private MeetTimeLabel() {
    }

    static String of(LocalDateTime when, LocalDateTime now) {
        return day(when.toLocalDate(), now.toLocalDate()) + " " + clock(when);
    }

    /** 바꿈 알림: 같은 날이면 앞쪽에만 날짜를 붙인다 — "오늘 오후 7시 30분 → 오후 8시". */
    static String change(LocalDateTime before, LocalDateTime after, LocalDateTime now) {
        if (before.toLocalDate().equals(after.toLocalDate())) {
            return of(before, now) + " → " + clock(after);
        }
        return of(before, now) + " → " + of(after, now);
    }

    private static String day(LocalDate date, LocalDate today) {
        if (date.equals(today)) return "오늘";
        if (date.equals(today.plusDays(1))) return "내일";
        return date.getMonthValue() + "월 " + date.getDayOfMonth() + "일";
    }

    private static String clock(LocalDateTime t) {
        int h = t.getHour();
        int h12 = h % 12 == 0 ? 12 : h % 12;
        String minute = t.getMinute() == 0 ? "" : " " + t.getMinute() + "분";
        return (h < 12 ? "오전" : "오후") + " " + h12 + "시" + minute;
    }
}

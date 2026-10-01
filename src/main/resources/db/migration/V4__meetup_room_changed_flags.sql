-- 모임방 장소·약속 시간을 "처음 정했는지 / 바꿨는지" 기억한다.
-- 알림은 "바뀌었어요"라고 했는데 방에 들어가면 "정했어요"로 보여 앞뒤가 안 맞았다.
-- 칸 추가만이고 기본값 false 라 기존 방은 "정했어요"로 보인다(이전 버전 앱도 그대로 동작).
ALTER TABLE meetup_rooms
    ADD COLUMN place_changed   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN meet_at_changed BOOLEAN NOT NULL DEFAULT FALSE;

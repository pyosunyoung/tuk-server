-- 모임방의 "약속 시간". 장소와 짝을 이루는 칸이다.
--
-- 왜 필요한가: 모임방은 장소는 기억하는데 시간은 못 기억했다. 약속을 잡고 나면
-- "몇 시였지"를 다시 확인할 곳이 없어서 반쪽짜리 메모였다.
--
-- expires_at 과는 다른 시각이다. expires_at 은 **모집 마감**(언제까지 합류할 수 있나)이고,
-- meet_at 은 **실제로 만나는 시각**이다. 약속 시간을 정한다고 만료를 늘리지 않는다 —
-- 늘리면 "시간 지나면 알아서 꺼져요"라는 약속이 깨진다.
--
--   meet_at        : 만나기로 한 시각. NULL = 아직 안 정함
--   meet_at_set_by : 마지막으로 정한 사람(장소와 같이 아무나 바꿀 수 있다)
--
-- 이전 버전 앱은 두 칸을 몰라도 그대로 동작한다(칸 추가만, 기존 칸 변경 없음).
ALTER TABLE meetup_rooms ADD COLUMN meet_at        timestamp(6);
ALTER TABLE meetup_rooms ADD COLUMN meet_at_set_by bigint;

ALTER TABLE meetup_rooms
    ADD CONSTRAINT fk_room_meet_at_set_by FOREIGN KEY (meet_at_set_by) REFERENCES users (id);

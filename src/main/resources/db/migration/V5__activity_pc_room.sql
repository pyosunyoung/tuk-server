-- 활동에 PC방 추가. 예전 앱은 모르는 값을 "아무거나"로 보여준다(Activity.fromWire).
--
-- 기존 검사 조건의 이름이 DB 마다 다르다: 운영은 V1 으로 만들어 ck_flash_activity,
-- 개발 DB 는 Flyway 이전에 Hibernate 가 만들어 flashes_activity_check. 이름 대신 내용으로 찾아 지운다.
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'flashes'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%activity%'
    LOOP
        EXECUTE format('ALTER TABLE flashes DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;

ALTER TABLE flashes ADD CONSTRAINT ck_flash_activity
    CHECK (activity IN ('MEAL', 'DRINK', 'CAFE', 'WALK', 'MOVIE', 'KARAOKE', 'PC_ROOM', 'ANYTHING'));

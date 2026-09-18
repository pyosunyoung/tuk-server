-- 모임방 장소를 "검색해서 고른 실제 장소"로 저장하기 위한 칸.
-- 둘 다 NULL 허용: 검색에 안 나오는 장소("학교 정문")는 기존처럼 이름만 직접 입력한다.
--   place_kakao_id : 카카오 장소 ID — 지도 앱에서 그 가게 페이지를 바로 연다
--   place_address  : 도로명(없으면 지번) 주소 — 같은 이름의 지점 구분용
-- 이전 버전 앱은 두 칸을 모른 채 place_name 만 보내도 그대로 동작한다(칸 추가만, 기존 칸 변경 없음).
ALTER TABLE meetup_rooms ADD COLUMN place_kakao_id varchar(30);
ALTER TABLE meetup_rooms ADD COLUMN place_address  varchar(255);

-- 초대 코드는 사람을 찾는 키라 겹치면 안 된다. 지금까지는 생성할 때 "이미 있는지"만 확인해서
-- 확인과 저장 사이에 같은 코드가 들어오면 막을 장치가 없었다. NULL(아직 코드를 안 만든 사용자)은 여러 개 허용된다.
CREATE UNIQUE INDEX uq_users_invite_code ON users (invite_code);

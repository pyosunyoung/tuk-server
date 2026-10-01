# 툭(Tuk) — 앱용 API 계약서

Flutter 앱 개발용. 이 문서는 백엔드 코드(컨트롤러/DTO)에서 뽑은 것으로, **실제 응답 필드명과 1:1로 일치**한다.
정본은 실행 중 서버의 Swagger: `http://localhost:8080/swagger-ui.html` (스키마 `/v3/api-docs`).

---

## 0. 기본

| 항목 | 값 |
|---|---|
| REST Base URL (안드 에뮬레이터) | `http://10.0.2.2:8080` |
| REST Base URL (iOS 시뮬레이터) | `http://localhost:8080` |
| WebSocket | `ws://10.0.2.2:8080/ws` (STOMP) |
| 인증 | `Authorization: Bearer <accessToken>` (로그인/재발급 제외 전부 필요) |
| 요청/응답 | JSON (UTF-8) |

### 토큰
- 로그인/재발급이 `{ accessToken, refreshToken }` 반환.
- **accessToken 수명 30분, refreshToken 14일.** access 만료 시 refresh 로 재발급(rotation — 재발급 시 refresh 도 새로 바뀜).
- 저장: `flutter_secure_storage`. dio 인터셉터에서 401 → refresh → 원요청 재시도 권장.
- refresh 는 **사용자당 1개(단일 세션)**. 다른 기기 로그인 시 이전 기기는 재로그인 필요.

### 공통 에러 포맷
모든 실패 응답 body:
```json
{ "message": "사람이 읽을 설명" }
```
| status | 의미 |
|---|---|
| 400 | 잘못된 요청/규칙 위반 (예: 자기 자신 친구신청, 이미 친구, 만료된 번개) |
| 401 | 토큰 없음/만료/무효, 또는 소셜 토큰 무효 |
| 403 | 권한 없음 (예: 초대 안 받은 번개 합류, 남의 모임방) |
| 404 | 리소스 없음 |
| 502 | 카카오 등 외부 API 장애 (우리 잘못 아님) |

### 공유 enum
- **Activity**: `MEAL` `DRINK` `CAFE` `WALK` `MOVIE` `KARAOKE` `ANYTHING`
- **Platform**: `IOS` `ANDROID`
- **친구 status**: `PENDING` `ACCEPTED`
- **번개 수신 myStatus**: `PENDING`(아직 합류 안 함) `JOINED`
- **모임방 status**: `OPEN` `CLOSED`

---

## 1. 화면별 API 매핑

### 🔐 로그인 화면
| 동작 | API | 요청 | 응답 |
|---|---|---|---|
| 카카오 로그인 | `POST /api/auth/login` | `{ "provider":"kakao", "token":"<카카오 access token>" }` | `{ accessToken, refreshToken }` |
| (개발용) dev 로그인 | `POST /api/auth/login` | `{ "provider":"dev", "token":"<임의 문자열>" }` | 동일 |

흐름: 카카오 SDK로 access token 획득 → 위 호출 → 토큰 저장 → **기기토큰 등록(아래)** + **WebSocket 연결(§2)** → 홈으로.
> `provider` 는 대소문자 무관. Apple 은 백엔드 미구현.

### 🔔 앱 시작 시(로그인 직후 1회)
| 동작 | API | 요청 |
|---|---|---|
| **내 정보 조회** | `GET /api/users/me` | (body 없음) → `{ userId, nickname, profileImage }` |
| **탈퇴** | `DELETE /api/users/me` | (body 없음) → `204`. 내가 띄운 툭·모임방, 받은 초대·합류, 친구 관계, 기기 토큰, 한가함 상태, refresh 토큰 삭제. 남의 모임방은 남기고 "정한 사람"만 비움. 카카오 연결 끊기는 앱이 SDK 로. 되돌릴 수 없음 |
| FCM 기기토큰 등록 | `POST /api/device-tokens` | `{ "token":"<FCM 토큰>", "platform":"ANDROID" }` |
| 로그아웃 시 토큰 해제 | `DELETE /api/device-tokens` | `{ "token":"<FCM 토큰>" }` |
| 로그아웃 | `POST /api/auth/logout` | (body 없음) |

> `userId` 는 앱이 **"이게 나"를 판단하는 기준**이다. 모임방 참여자 목록에서 '나' 표시,
> 장소를 정한 사람이 나인지, 홈 프로필 아바타에 쓴다. 로그인 직후 받아 세션 동안 들고 있으면 된다.
> 토큰은 유효한데 유저가 없으면(탈퇴·DB 초기화) `404` — 앱은 세션을 지우고 로그인 화면으로.

### 🏠 홈 — "지금 한가한 친구"
| 동작 | API | 응답 |
|---|---|---|
| 한가한 친구 목록 | `GET /api/presence/friends` | `[{ userId, nickname, profileImage, activity, remainingSeconds }]` |
| 내 콜 상태 | `GET /api/presence/me` | `{ available, activity, remainingSeconds }` (없으면 `available:false`, 나머지 null) |
| 콜 켜기 | `POST /api/presence` | 요청 `{ "activity":"CAFE", "durationMinutes":60 }` → `{ available:true, activity, remainingSeconds }` |
| 콜 끄기 | `DELETE /api/presence` | (body 없음) |

- `durationMinutes`: 1~720. `remainingSeconds`: 남은 TTL(초).
- **실시간**: `/user/queue/presence` 구독 시, 친구가 콜 켜고/끄면 push 옴 → 목록 갱신(§2).

### 👥 친구 화면
| 동작 | API | 요청 | 응답 |
|---|---|---|---|
| 내 친구 목록 | `GET /api/friendships` | — | `[{ userId, nickname, profileImage }]` |
| 받은 친구 신청 | `GET /api/friendships/requests` | — | `[{ friendshipId, requesterId, nickname, profileImage }]` |
| 친구 신청 | `POST /api/friendships` | `{ "addresseeId": 341 }` | `{ friendshipId, status }` (status=`PENDING`, 상대가 이미 나에게 신청했으면 `ACCEPTED`) |
| 신청 수락 | `POST /api/friendships/{friendshipId}/accept` | — | 200 (body 없음) |
| 내 초대코드 | `GET /api/friendships/invite-code` | — | `{ code }` (없으면 생성해서 반환. 8자, 혼동문자 제외) |
| 친구 끊기 | `DELETE /api/friendships/friends/{friendUserId}` | (body 없음) | `204`. 양쪽 목록에서 사라짐, 알림 없음. 친구가 아니면 404 |
| 보낸 신청 취소 | `DELETE /api/friendships/requests/{friendshipId}` | (body 없음) | `204`. 내가 보낸 PENDING 만. 받은 신청은 403(거절 API 는 만들지 않는다 — 상대 "보낸 신청"에서 사라져 거절이 드러남) |
| 코드로 친구맺기 | `POST /api/friendships/redeem` | `{ "code": "AB3K9XQ2" }` | `{ friendshipId, status }` (`POST /friendships` 와 동일 규칙) |

- 거절 API는 **없음**(설계 원칙 "거절 안 보임"). 무시 = 그냥 두면 됨.
- **친구 추가는 초대코드 방식**(닫힌 절친 원칙에 맞춰 전체검색 대신 채택): 내 코드를 상대에게 알려주고, 상대가 `redeem` 하면 친구 신청이 감. 서로 코드를 교환하면 맞신청으로 즉시 `ACCEPTED`.
  - `redeem` 은 내부적으로 코드 주인에게 `sendRequest` → 이미 친구면 400, 본인 코드면 400, 없는 코드면 404.
  - 없는 코드를 **10분에 10번** 넣으면 그 뒤로는 **429**(`코드를 여러 번 잘못 넣었어요. 10분 뒤에 다시 시도해 주세요.`). 무작위 대입 방지 — 틀린 경우만 센다. 앱은 메시지를 그대로 보여주면 된다.
  - `addresseeId` 직접 신청 경로도 그대로 유효(디버그/내부용).

### ⚡ 번개 발송
| 동작 | API | 요청 | 응답 |
|---|---|---|---|
| 번개 발송 | `POST /api/flashes` | `{ "activity":"MEAL", "message":"밥ㄱㄱ", "targetUserIds":[341], "durationMinutes":60, "scheduledAt":null }` | `{ flashId, roomId, activity, scheduledAt, expiresAt, recipientCount }` |

- `message`(≤200)·`durationMinutes`(기본 60)·`scheduledAt`(null=지금, 미래시각=예약)·`targetUserIds`(비우면 절친 전체) 모두 선택.
- 서버가 `targetUserIds`를 **내 절친과 교집합해 재검증** → 비절친은 자동 제외(`recipientCount`로 실제 발송 수 확인).

### 📥 받은 번개 / 합류 / 모임방
| 동작 | API | 응답 |
|---|---|---|
| 받은 활성 번개 | `GET /api/flashes/received` | `[{ flashId, senderId, senderNickname, senderProfileImage, activity, message, scheduledAt, expiresAt, meetAt, visibleUntil, myStatus }]` |
| 내가 띄운 활성 번개 | `GET /api/flashes/sent` | `[{ flashId, activity, message, scheduledAt, expiresAt, meetAt, visibleUntil, recipientIds:[341], joinedMembers:[{ userId, nickname, profileImage, sender:false }] }]` (홈 "내가 띄운 번개" 배너용. 시작된 ACTIVE 번개만, 합류자만 포함, `{N}명 모였어요`=joinedMembers 수) |

- `visibleUntil` = **목록에서 치울 시각.** 앱은 이 값만 보고 지운다(규칙을 앱에 따로 두지 말 것).
  성사 안 됨(합류자 0명 / 나는 미합류) → 합류 마감까지 · 성사 + 약속 시간 있음 → 마감과 약속 중 늦은 쪽 ·
  **성사 + 약속 시간 없음 → 마감 후 6시간**. 규칙은 서버 `MeetupLifecycle` 한 곳에 있다.
| 합류 | `POST /api/flashes/{flashId}/join` | `MeetupRoom`(아래) — 멱등(이미 JOINED면 방만 반환) |
| 모임방 조회 | `GET /api/flashes/{flashId}/room` | `MeetupRoom` |
| 장소 설정 | `PATCH /api/flashes/{flashId}/room/place` | 요청 `{ "placeName":"홍대 A카페", "placeLat":37.5, "placeLng":127.0 }` → `MeetupRoom` |
| 약속 시간 설정 | `PATCH /api/flashes/{flashId}/room/time` | 요청 `{ "meetAt":"2026-09-24T20:00:00" }` → `MeetupRoom` |

**MeetupRoom 응답 형태:**
```json
{
  "roomId": 20, "flashId": 85, "activity": "MEAL",
  "placeName": null, "placeLat": null, "placeLng": null,
  "placeKakaoId": null, "placeAddress": null,
  "placeSetById": null, "placeSetByNickname": null, "placeChanged": false,
  "meetAt": null, "meetAtSetById": null, "meetAtSetByNickname": null, "meetAtChanged": false,
  "status": "OPEN",
  "expiresAt": "2026-09-22T21:30:00",
  "members": [
    { "userId": 340, "nickname": "...", "profileImage": null, "sender": true },
    { "userId": 341, "nickname": "...", "profileImage": null, "sender": false }
  ]
}
```
- `members` = 보낸 사람(`sender:true`) + **JOINED 수신자만**. 아직 합류 안 한 사람은 안 보임.
- `placeSetById`/`placeSetByNickname` = **장소를 정한 멤버**. 아무도 안 정했으면 둘 다 null.
  장소는 멤버 누구나 다시 정할 수 있고, **마지막으로 바꾼 사람**으로 덮인다.
  (`placeSetById == 내 userId` 면 "내가 정했어요")
- `placeKakaoId`/`placeAddress` = **검색으로 고른 장소**일 때만 값이 있다(직접 입력이면 둘 다 null).
- `placeChanged`/`meetAtChanged` = 마지막 동작이 "바꿈"이었나(처음 정함이면 false). 화면의 "OO님이 정했어요/바꿨어요"에 쓴다. 같은 값으로 다시 저장하면 아무것도 바뀌지 않는다(정한 사람도 그대로, 알림 없음).
- `meetAt` = **만나기로 한 시각**(아직 안 정했으면 null). `meetAtSetById`/`Nickname` 은 마지막으로 정한 멤버 —
  장소와 같이 **멤버 아무나** 정하고 아무나 바꾼다.
- `expiresAt` = **모집 마감 시각**(언제까지 합류할 수 있나). 목록을 거치지 않고
  **공유 링크로 방에 바로 들어오는 경로**가 있어서 방 응답에도 담는다.
- ⚠️ **`meetAt` 과 `expiresAt` 은 다른 시각이다.** 약속 시간을 정해도 만료는 늘어나지 않는다 —
  늘리면 "시간 지나면 알아서 꺼져요"라는 제품 약속이 깨진다. 화면에 둘을 같이 보일 땐 반드시 라벨을 붙일 것.
  (만료돼도 방은 지워지지 않고 `status: CLOSED` 가 될 뿐이라, 링크로는 계속 열린다)
- 약속 시간 범위: **과거 1시간 ~ 미래 24시간**. 벗어나면 400.
- 합류 권한: 초대받은(수신 레코드 있는) 사람만. 아니면 403.
- **실시간**: `/user/queue/flash` 구독 시 새 번개(`NEW_FLASH`)·누가 합류(`MEMBER_JOINED`)·장소/시간 정함·바꿈(`PLACE_UPDATED`/`MEET_AT_UPDATED`) push(§2).

---

## 2. WebSocket (STOMP) — 실시간

```
연결:   ws://10.0.2.2:8080/ws
CONNECT 헤더:  Authorization: Bearer <accessToken>   (필수 — 없으면 연결 거부)
구독:   /user/queue/presence    (내게 온 presence 이벤트)
        /user/queue/flash       (내게 온 번개 이벤트)
```
- 로그인 후 1회 연결, 로그아웃 시 해제. `stomp_dart_client` 사용.
- 오프라인이면 WS 대신 **FCM 푸시**로 같은 내용이 옴(서버가 알아서 라우팅).

**`/user/queue/presence` 이벤트** (`PresenceEvent`):
```json
{ "type":"AVAILABLE", "userId":341, "nickname":"영희", "profileImage":null,
  "activity":"CAFE", "remainingSeconds":3600 }
```
`type`: `AVAILABLE`(친구가 콜 켬) | `UNAVAILABLE`(콜 끔 — 프로필/활동 null).

**`/user/queue/flash` 이벤트** (`FlashPushEvent`):
```json
{ "type":"NEW_FLASH", "flashId":85, "userId":340, "nickname":"철수", "profileImage":null,
  "activity":"MEAL", "message":"밥ㄱㄱ", "scheduledAt":null, "expiresAt":"2026-07-23T20:42:02" }
```
`type`: `NEW_FLASH`(새 번개 도착 — `userId`=보낸이) | `MEMBER_JOINED`(누가 합류 — `userId`=합류자, activity/message 등 null)
| `PLACE_UPDATED`(장소 정함/바꿈 — `userId`=정한 사람, `placeBefore`(처음이면 null)·`placeAfter`)
| `MEET_AT_UPDATED`(약속 시간 정함/바꿈 — `userId`=정한 사람, `meetAtBefore`(처음이면 null)·`meetAtAfter`).
- 합류·장소·시간 이벤트에도 `activity` 가 실린다(어느 모임인지). 푸시 제목은 "밥 · 장소가 바뀌었어요"처럼 활동이 앞에 붙는다.
- 장소/시간 이벤트는 **정한 사람을 뺀 멤버**에게만 간다. 같은 값으로 다시 저장하면 보내지 않는다.
- 미접속 멤버에겐 같은 내용이 FCM 으로 간다(`data.type` 동일, `data.flashId`). 제목이 "정해졌어요"/"바뀌었어요"로 갈리고, 바꿈은 `이전 → 이후` 를 보여준다.

---

## 3. 참고
- 날짜(`scheduledAt`/`expiresAt`)는 ISO-8601 `LocalDateTime`(타임존 없음) 문자열.
- presence·번개는 시간 지나면 서버에서 자동 만료 → 앱은 목록 새로고침만 하면 됨.
- 실행: 백엔드 폴더에서 `./gradlew bootRun --args='--spring.profiles.active=dev'` (dev 로그인 활성화).

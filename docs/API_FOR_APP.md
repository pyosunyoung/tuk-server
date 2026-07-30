# 번개(bungae) — 앱용 API 계약서

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
| FCM 기기토큰 등록 | `POST /api/device-tokens` | `{ "token":"<FCM 토큰>", "platform":"ANDROID" }` |
| 로그아웃 시 토큰 해제 | `DELETE /api/device-tokens` | `{ "token":"<FCM 토큰>" }` |
| 로그아웃 | `POST /api/auth/logout` | (body 없음) |

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

- 거절 API는 **없음**(설계 원칙 "거절 안 보임"). 무시 = 그냥 두면 됨.
- ⚠️ 친구 신청은 **상대의 userId(`addresseeId`)를 알아야** 함. 현재 유저 검색 API 없음 → **백엔드에 `GET /users/me`·친구검색 추가 필요할 수 있음**(앱 설계하며 결정).

### ⚡ 번개 발송
| 동작 | API | 요청 | 응답 |
|---|---|---|---|
| 번개 발송 | `POST /api/flashes` | `{ "activity":"MEAL", "message":"밥ㄱㄱ", "targetUserIds":[341], "durationMinutes":60, "scheduledAt":null }` | `{ flashId, roomId, activity, scheduledAt, expiresAt, recipientCount }` |

- `message`(≤200)·`durationMinutes`(기본 60)·`scheduledAt`(null=지금, 미래시각=예약)·`targetUserIds`(비우면 절친 전체) 모두 선택.
- 서버가 `targetUserIds`를 **내 절친과 교집합해 재검증** → 비절친은 자동 제외(`recipientCount`로 실제 발송 수 확인).

### 📥 받은 번개 / 합류 / 모임방
| 동작 | API | 응답 |
|---|---|---|
| 받은 활성 번개 | `GET /api/flashes/received` | `[{ flashId, senderId, senderNickname, senderProfileImage, activity, message, scheduledAt, expiresAt, myStatus }]` |
| 합류 | `POST /api/flashes/{flashId}/join` | `MeetupRoom`(아래) — 멱등(이미 JOINED면 방만 반환) |
| 모임방 조회 | `GET /api/flashes/{flashId}/room` | `MeetupRoom` |
| 장소 설정 | `PATCH /api/flashes/{flashId}/room/place` | 요청 `{ "placeName":"홍대 A카페", "placeLat":37.5, "placeLng":127.0 }` → `MeetupRoom` |

**MeetupRoom 응답 형태:**
```json
{
  "roomId": 20, "flashId": 85, "activity": "MEAL",
  "placeName": null, "placeLat": null, "placeLng": null,
  "status": "OPEN",
  "members": [
    { "userId": 340, "nickname": "...", "profileImage": null, "sender": true },
    { "userId": 341, "nickname": "...", "profileImage": null, "sender": false }
  ]
}
```
- `members` = 보낸 사람(`sender:true`) + **JOINED 수신자만**. 아직 합류 안 한 사람은 안 보임.
- 합류 권한: 초대받은(수신 레코드 있는) 사람만. 아니면 403.
- **실시간**: `/user/queue/flash` 구독 시 새 번개(`NEW_FLASH`)·누가 합류(`MEMBER_JOINED`) push(§2).

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
`type`: `NEW_FLASH`(새 번개 도착 — `userId`=보낸이) | `MEMBER_JOINED`(누가 합류 — `userId`=합류자, activity/message 등 null).

---

## 3. 참고
- 날짜(`scheduledAt`/`expiresAt`)는 ISO-8601 `LocalDateTime`(타임존 없음) 문자열.
- presence·번개는 시간 지나면 서버에서 자동 만료 → 앱은 목록 새로고침만 하면 됨.
- 실행: 백엔드 폴더에서 `./gradlew bootRun --args='--spring.profiles.active=dev'` (dev 로그인 활성화).

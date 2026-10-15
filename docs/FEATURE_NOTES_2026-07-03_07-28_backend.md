# 기능 정리본 — 2026-07-03 ~ 07-28 (백엔드 MVP 0→1)

백엔드를 **맨땅에서 7개 레이어로 세운** 기록이다(①셋업/Repository → ②인증/JWT → ③Friendship → ④Presence → ⑤실시간(STOMP) → ⑥Flash → ⑦FCM, + Swagger·카카오 실연동).
시간순·커밋 단위 기록은 앱 리포 `tuk-app/docs/DEV_LOG.md`에, 이 문서는 **배경 → 선정 이유 → 구현 → Before/After → 테스트 → 남은 과제** 구조로 **설계 결정과 검증에서 잡은 결함** 중심으로 다시 묶은 것이다.

- **범위**: 백엔드 `tuk-server`만. git 첫 커밋 `5cf0cd2`(2026-07-03) ~ `8f178ca`(2026-07-28).
- **출발점(이 창 이전에 이미 있던 것)**: Spring Boot 스캐폴드 + **엔티티 9개(평탄 패키지)** + 설계 문서(CLAUDE.md)가 **커밋 안 된 채** 디스크에 있었다. 이 창은 거기서 `git init` → 구조 재편·버그 수정 → 로직 레이어 전체(Repository/Service/Controller)·인증·실시간·FCM을 올린 것이다. **엔티티 설계 자체는 선행 작업**이고, 이 문서는 그 위에 쌓은 부분을 다룬다.
- **이름/용어 주의**: 이 시기 코드네임은 `bungae`(자바 `com.bungae`). 2026-08-30 커밋 `8038704`에서 제품명 `tuk`(`com.tuk`)으로 통일됐다. 아래 패키지·클래스명은 **당시(bungae) 기준**이며, 현재 코드는 `com.tuk`로 바뀌어 있다.
- **증거 형태**: 이 범위는 **백엔드(API·실시간·테스트)라 UI 캡처가 없다.** 대신 **테스트 결과(최종 68개 그린)·`curl`/`psql` 응답·실계정 로그인으로 생성된 DB 행**이 Before/After 증거다. 경력기술서엔 이쪽이 오히려 설득력 있을 수 있다.
- 스택: Spring Boot **4.0.7** / Java 17, Spring Data JPA·Redis, Spring Security + JWT(jjwt), WebSocket(STOMP), PostgreSQL · Redis · FCM.

> 분류 표기: 🆕 신규 기능 · 🧭 설계/선정 · ⚙️ 개선 · 🐞 오류 해결 · 🧪 테스트

---

## 1. 셋업 · 구조 재편 · Repository + 핵심 쿼리 🧭 🐞 🧪

### 배경/문제
엔티티와 스캐폴드는 있었지만 **프로젝트가 돌지 않는 상태**였다. 엔티티가 전부 `com.bungae` 최상위에 평탄하게 있었고, 메인 앱은 `com.bungae.bungae_server`에 있어 **`@SpringBootApplication`의 컴포넌트 스캔 범위가 엔티티를 못 잡았다**(스캔 루트 밖). `application.yml`(DB/Redis 연결)도 없었다.

### 선정 이유
| 결정 | 이유 |
|---|---|
| 메인 앱을 `com.bungae`로 올림 | 스캔 루트를 `com.bungae.*`로 맞춰 전 도메인·엔티티가 잡히게 |
| 도메인별 패키지(`user`/`friendship`/`presence`/`flash`/`notification`/`global`) | 레이어드(Controller→Service→Repository) + 도메인 응집 |
| "지금 한가한 친구"를 **단일 쿼리 대신 2단계 조합** | Presence는 Redis, Friendship은 RDB → JOIN 불가. 각 저장소 특성을 살림 |
| `@DataJpaTest`로 핵심 쿼리를 못 박음 | 쿼리 조건이 하나라도 빠지면 CI에서 바로 빨간불 |

### 구현/해결 내용
- 엔티티 9종을 도메인 `*.domain`으로, 공통(BaseTimeEntity·Activity)은 `global.common`으로 이동. `JpaAuditingConfig`로 `@EnableJpaAuditing` 분리(테스트 슬라이스에서 제외 가능).
- `application.yml`: 로컬 PostgreSQL + Redis, `open-in-view: false`, `default_batch_fetch_size: 100`(LAZY N+1 완화). `docker-compose.yml`(PG16/Redis7)로 여러 머신 재현.
- **Repository 7종 + 핵심 쿼리 2개**:
  - "지금 한가한 친구": `FriendshipRepository.findAcceptedFriendIds(me)`(RDB) → `PresenceRepository.findAllById(ids)`(Redis) → 살아있는 TTL만. **만료자는 Redis에 없어 쿼리 없이 자연 제외**.
  - "내가 받은 활성 번개": `FlashRecipientRepository.findActiveReceived` — `join fetch`로 flash+sender 함께 로딩(목록 N+1 방지).

### Before / After
| | Before | After |
|---|---|---|
| 컴포넌트 스캔 | 메인앱 `com.bungae.bungae_server` → 엔티티(`com.bungae.*`) **스캔 안 됨** | 메인앱 `com.bungae` → 전 도메인 스캔, 컨텍스트 기동 |
| 핵심 쿼리 검증 | 없음 | `@DataJpaTest` 5개(방향무관 절친·활성번개 필터 등) 그린 |

### 테스트
`@DataJpaTest`(실 PostgreSQL, `replace=NONE`, 롤백) — `findAcceptedFriendIds`(방향무관·자기/PENDING 제외), `findActiveReceived`(ACTIVE+만료전만, PENDING/JOINED 둘 다) 등.

### 남은 과제
Repository 위에 Service/Controller(각 기능 항목에서), Boot 4 슬라이스 패키지 함정(→ 9번).

> Git: `5cf0cd2` 셋업+엔티티 재편+Repository · `606d45f` docker-compose

---

## 2. 인증 — 소셜 로그인 추상화 + 자체 JWT(access/refresh rotation) 🆕 🧭

### 배경/문제
앱이 소셜 SDK로 받은 토큰을 백엔드가 검증하고 **자체 JWT(access+refresh)**를 발급해야 했다. 소셜 제공자(카카오/애플)는 늘어날 수 있고, 실제 카카오 키 없이도 개발·테스트가 가능해야 했다.

### 선정 이유
| 결정 | 이유 |
|---|---|
| `SocialClient` 인터페이스 + `SocialClientRouter` | provider 추가 = 구현체 하나. 카카오 실패가 다른 제공자에 영향 없음 |
| refresh 저장 = **Redis**(`refresh:{userId}`, TTL) | rotation·강제 로그아웃·자동 만료에 적합 |
| `RefreshTokenStore` 인터페이스 분리 | AuthService를 Redis 없이 순수 단위 테스트(인메모리 fake) |
| JWT 필터 `@Component` 미부착, SecurityConfig에서 `new` | @Component면 Boot가 서블릿 필터로도 자동 등록 → 이중 실행 |
| principal = `userId`(Long) | 컨트롤러에서 `@AuthenticationPrincipal Long`로 바로 수령 |

### 구현/해결 내용
- `JwtTokenProvider`(jjwt): access(30m)/refresh(14d), `type` 클레임으로 구분. `JwtAuthenticationFilter`(Bearer 검증 → SecurityContext). `SecurityConfig`(stateless, `/api/auth/login·refresh` permitAll).
- `SocialClient` + `KakaoClient`(kapi.kakao.com/v2/user/me) + `RedisRefreshTokenStore`. `GlobalExceptionHandler`로 `BusinessException` 계층(401/404/400/403) 일관 응답.
- 엔드포인트: `POST /api/auth/login`·`/refresh`·`/logout`.

### Before / After
그린필드라 Before 없음. (단, rotation 토큰 중복/원자성 결함은 **8번**에서 Before/After로 다룸 — 이 기능의 검증이 잡은 것들.)

### 테스트
`JwtTokenProviderTest`(왕복·type 구분·변조·만료), `AuthServiceTest`(신규/기존 로그인, rotation, 재사용 탐지, access를 refresh로 넘기면 거부) — Mockito + 인메모리 store.

### 남은 과제
rotation 원자화·jti(→ 8번), 실제 카카오 연동(→ 9번), 애플 로그인(미구현), 멀티기기 refresh(MVP는 단일 세션 의도).

> Git: `2d2a326` 소셜 로그인 + 자체 JWT

---

## 3. Friendship — 절친 추가·수락·맞신청·"거절 안 보임" 🆕 🧭

### 배경/문제
절친 관계(자기참조 N:N)를 신청/수락으로 맺되, **거절이 상대에게 드러나지 않아야** 했다(제품 원칙).

### 선정 이유
| 결정 | 이유 |
|---|---|
| `FriendshipStatus` = `PENDING`/`ACCEPTED`만, **DECLINED 없음** | 스키마로 "거절 안 보임" 보장. 무시 = PENDING 방치 |
| 거절 API·이벤트를 **만들지 않음** | 무응답이 상대에게 전달되지 않는 게 핵심 |
| 상대가 이미 나에게 신청한 상태면 **맞신청 자동 수락** | 자연스러운 UX |
| 공통 예외 계층(`BusinessException`) 도입 | Friendship에서 쓴 404/400/403을 이후 전 기능이 재사용 |

### 구현/해결 내용
- `FriendshipService`: `sendRequest`(자기신청/중복/이미친구 방어 + 맞신청 자동수락), `accept`(수신자만), 받은 신청·친구 목록. 상태 변경은 도메인 메서드(`accept()`).
- `FriendshipController`: `POST /api/friendships`·`/{id}/accept`, `GET /requests`·`GET /api/friendships`.

### Before / After
그린필드. (방향성 유니크 제약의 경합 결함은 **8번**에서 Before/After로.)

### 테스트
`FriendshipServiceTest`(Mockito) — 자기신청(400)·미존재(404)·신규PENDING·**맞신청 자동수락**·이미친구(400)·중복(400)·수락 권한(403)·정상·미존재신청(404).

### 남은 과제
방향무관 유니크로 경합 차단(→ 8번), 유저 검색/내 정보(이후 세션 `GET /users/me`), 친구 끊기(이후).

> Git: `109850c` 절친 추가·수락

---

## 4. Presence — Redis TTL "지금 한가한 친구" 🆕 🧭

### 배경/문제
"지금 한가함"은 **시간이 지나면 알아서 꺼져야** 한다(토글을 직접 안 꺼도). 친구가 조회할 땐 **내 절친 중 지금 살아있는 사람만** 보여야 한다.

### 선정 이유
| 결정 | 이유 |
|---|---|
| Presence를 RDB 아닌 **Redis `@RedisHash` + `@TimeToLive`** | TTL = "자동 만료" 원칙을 코드 없이 구현 |
| 조회 = 절친ID(RDB) ∩ 살아있는 presence(Redis) | 서로 다른 저장소를 2단계로 조합(1번 참조) |
| 절친 재검증을 **서버에서** | 클라가 보낸 대상 그대로 신뢰 금지(닫힌 절친 원칙) |

### 구현/해결 내용
- `PresenceService.turnOn/turnOff/getMyPresence/getAvailableFriends`. `getAvailableFriends`: `findAcceptedFriendIds` → `presenceRepository.findAllById` → 유저정보 결합.
- 엔드포인트: `POST/DELETE /api/presence`, `GET /me`, `GET /friends`.

### Before / After
그린필드. (증거: `curl`로 콜 켜기 → `{"available":true,"activity":"CAFE","remainingSeconds":3600}`, 친구 조회에 TTL 살아있는 대상만.)

### 테스트
`PresenceServiceTest`(Mockito) — present 필터(절친 3명 중 2명만), 친구 0명이면 Redis 조회조차 안 함(단락), 아무도 안 켜면 빈 목록, turnOn 저장 필드, turnOff.

### 남은 과제
콜 상태 변화를 접속 친구에게 실시간 전파(→ 5번).

> Git: `40755d1` 콜 켜기·조회(Presence, Redis)

---

## 5. 실시간 전파 — WebSocket(STOMP) + "커밋 이후" 전파 🆕 🧭 🐞

### 배경/문제
콜/번개 변화를 **"절친 중 접속자"에게 실시간**으로 밀어야 했다. WebSocket은 HTTP와 달리 CONNECT 시점 인증이 필요하고, 전파가 **본 작업(트랜잭션)을 깨거나 롤백 시 유령 알림**을 내선 안 됐다.

### 선정 이유
| 결정 | 이유 |
|---|---|
| 전송 = Spring user-destination(`convertAndSendToUser`) | userId→세션 라우팅을 Spring이 처리, 오프라인은 자동 무시 |
| CONNECT 헤더 JWT 인증(`StompAuthChannelInterceptor`) | 핸드셰이크는 permitAll, 실제 인증은 STOMP CONNECT에서 |
| 세션 레지스트리(`userId↔세션`, 다중기기) 직접 구현 | "온라인 여부" 판단 + 멀티서버 Redis Pub/Sub 확장의 씨앗 |
| 전파를 **부수효과로 격리**(try/catch per 대상) | 한 전송 실패가 본 작업·다른 전파를 깨지 않게 |
| Flash 전파는 **`@TransactionalEventListener(AFTER_COMMIT)`** | 커밋 전 전파 금지 — 롤백되면 유령 알림. payload는 트랜잭션 안에서 완성(커밋 후 lazy 로딩 회피) |

### 구현/해결 내용
- `WebSocketConfig`(STOMP `/ws`, SimpleBroker, user prefix), `StompAuthChannelInterceptor`, `WebSocketSessionRegistry`, `PresenceEventBroadcaster`.
- Flash는 서비스가 트랜잭션 안에서 **이벤트만 발행** → 리스너(`FlashEventBroadcaster`)가 커밋 후 전파.

### Before / After
| | Before | After |
|---|---|---|
| presence 전파 실패 | `convertAndSendToUser`가 던지면 `turnOn`이 500 → Redis엔 이미 저장(불일치) | 대상별 try/catch 격리 → 본 작업 성공, 로그만 |
| Flash 전파 타이밍 | (설계 단계 경고) 커밋 전 전파 시 롤백=유령 알림 | AFTER_COMMIT으로 커밋 성공 후에만 전파 |

### 테스트
`WebSocketSessionRegistryTest`(멀티세션 — 마지막 세션 끊겨야 오프라인), `StompAuthChannelInterceptorTest`(유효/없음/refresh거부), `PresenceEventBroadcasterTest`(온라인만·실패 격리).

### 남은 과제
Flash 실시간(발송/합류 전파)은 6·7번에서 이어감. 멀티서버 시 SimpleBroker→Redis 릴레이.

> Git: `734d70c` WebSocket presence 전파 · `1882660` 전파 부수효과 격리

---

## 6. Flash — 발송·합류·모임방 + 만료 스케줄러 (4대 원칙) 🆕 🧭

### 배경/문제
이 앱의 심장부. 발송 → 수신(PENDING) → 합류(JOINED) → 모임방을 잇되, 제품 4대 원칙을 **코드로** 강제해야 했다.

### 선정 이유 / 4대 원칙이 코드에 박힌 지점
| 원칙 | 구현 |
|---|---|
| 거절 안 보임 | decline 엔드포인트 **자체를 안 만듦**. 모임방 멤버 = 보낸이 + **JOINED만** |
| 자동 만료 | `expires_at` + `@Scheduled` 스위퍼(ACTIVE 번개·OPEN 방을 도메인 메서드 `expire()`/`close()`로) |
| 멱등 합류 | `UNIQUE(flash_id,user_id)` + 이미 JOINED면 no-op |
| 닫힌 절친 재검증 | 클라가 보낸 `targetUserIds`를 **내 절친과 교집합** → 비절친 자동 탈락(`recipientCount`) |

### 구현/해결 내용
- `FlashService`(sendFlash/join/getRoom/setPlace/getReceivedFlashes), `FlashExpirationScheduler`, `MeetupRoom`(Flash 1:1). 만료는 벌크 update 대신 **조회→도메인 메서드**(dirty checking)로 컨벤션 준수.
- 엔드포인트: `POST /api/flashes`·`GET /received`·`POST /{id}/join`·`GET /{id}/room`·`PATCH /{id}/room/place`.

### Before / After
그린필드. (증거: 실서버 스모크 — 미초대자 합류 **403**, 재합류 **200**(멱등), presence TTL 3600s 자동만료 — 10번.)

### 테스트
`FlashServiceTest`(10) — 재검증(비절친 제외)·대상없음·예약과거·합류(미초대403/만료400/성공/멱등)·모임방 권한(403)·장소설정, `FlashExpirationSchedulerTest`(스위퍼가 EXPIRED/CLOSED 전환).

### 남은 과제
발송/합류 실시간 전파(→ 7번), 오프라인 대상 푸시(→ 7번).

> Git: `5a6f9c5` 번개 발송·합류·모임방·만료 스케줄러

---

## 7. FCM — 온라인=WebSocket / 오프라인=FCM 라우팅 🆕 🧭

### 배경/문제
WebSocket 전파는 **접속자만** 받는다. 오프라인 친구는 번개를 놓친다 — 이 구멍을 FCM이 메운다. 단, Firebase 키 없이도 앱이 뜨고 테스트돼야 했다.

### 선정 이유
| 결정 | 이유 |
|---|---|
| `PushSender` 인터페이스(기본 `LoggingPushSender`, `fcm.enabled=true`면 `FcmPushSender`) | 키 없이도 기동·테스트. 소셜 로그인의 `SocialClient`와 같은 패턴 |
| 같은 `FlashBroadcastEvent`를 **두 리스너가 분담** | `FlashEventBroadcaster`(접속→WS) / `FlashPushListener`(미접속→FCM). 관심사 분리, 둘 다 AFTER_COMMIT |
| presence(콜 켜기/끄기)는 **FCM 제외** | 앱 열면 보이는 주변 상태 — 푸시하면 시끄러움 |
| `UNREGISTERED` 토큰 자동 정리 | 발송 결과로 죽은 토큰 반환 → 서비스가 삭제 |

### 구현/해결 내용
- `PushSender`/`LoggingPushSender`/`FcmPushSender`/`FirebaseConfig`(조건부), `DeviceTokenController/Service`(등록 멱등·기기 계정전환·해제는 본인만), `PushNotificationService`(REQUIRES_NEW), `FlashPushListener`(오프라인 필터 + 문구 구성).

### Before / After
| | Before | After |
|---|---|---|
| 오프라인 대상 | WebSocket 전파를 못 받고 번개 유실 | 접속=WS / 미접속=FCM로 커버 |

### 테스트
`DeviceTokenServiceTest`(등록/재등록/해제 권한), `PushNotificationServiceTest`(발송·죽은토큰 정리), `FlashPushListenerTest`(오프라인 필터·문구).

### 남은 과제
실제 FCM 키 연동(이후 세션), 발송 비동기화(스케일).

> Git: `fcba4ec` FCM 푸시 알림 — 오프라인 대상 커버

---

## 8. 검증·리뷰가 잡은 동시성 결함 3건 🐞 🧪

> 이 창에서 가장 포폴에 강한 부분 — **단위 테스트와 코드 리뷰가 실제 동시성 결함을 잡았고, Before/After가 뚜렷하다.**

### 배경/문제
① refresh rotation을 짜고 테스트하니 **새 토큰이 옛 토큰과 완전히 동일**했다. ② rotation의 읽기-비교-쓰기가 원자적이지 않았다. ③ 친구관계 유니크가 방향성이라 동시 맞신청 시 중복 쌍이 생길 수 있었다.

### 선정 이유 / 해결
| 결함 | 원인 | 해결 |
|---|---|---|
| **rotation 토큰 중복** | JWT `iat/exp`가 초 단위 → 같은 초·같은 userId면 payload 동일 → 토큰 문자열까지 동일 | 모든 토큰에 **`jti`(UUID)** 추가 → 유일화 |
| **rotation 비원자** | Redis 읽고→비교→쓰기 사이 경합 → 동시 요청이 서로 덮어씀 | **Lua 스크립트**로 "비교→교체/파기"를 원자화(재사용 탐지도 원자성 안에) |
| **친구 중복 쌍** | `UNIQUE(requester,addressee)`는 방향성 → A→B·B→A 둘 다 허용 | 정규화 쌍 `(user_low_id,user_high_id)`에 UNIQUE + `saveAndFlush`로 경합 패자를 500 대신 **400**으로 번역 |

### Before / After
| | Before | After |
|---|---|---|
| rotation 토큰 | `createRefreshToken` 두 번 → **같은 문자열**(rotation 무의미) | jti로 매 토큰 유일 → rotation 성립 |
| rotation 경합 | 동시 2요청 → 둘 다 통과, 토큰 하나 즉시 무효 | Lua CAS → 하나만 ROTATED, 나머지 REUSE |
| 친구 중복 | 동시 맞신청 → 쌍 2행, 친구목록 중복 | DB가 반대쌍 거부, 패자 400 |

### 테스트
- `jti`: `refreshRotation` 테스트가 "새 토큰 ≠ 옛 토큰"을 단언 → **최초엔 실패** → jti 추가로 통과(테스트가 결함을 먼저 잡음).
- 친구 유니크: psql로 제약 교체 후 `reversePairRejected`(@DataJpaTest) — 반대쪽 쌍 저장 시 `DataIntegrityViolationException` 단언.
- rotation 원자: `AuthServiceTest`에 NOT_FOUND·REUSE 케이스 추가.

![TODO: 테스트 리포트 — rotation 실패→jti 수정 후 그린 (선택: 캡처/로그)](images/backend-jti-test.png)

### 남은 과제
멀티서버 시 rotation은 Redis 단일 인스턴스 가정(현 구조 유효).

> Git: `2d2a326`(jti, auth에 포함) · `21e44dc` rotation 원자화 + 친구 방향무관 유니크

---

## 9. 카카오 실연동 · 비밀값 외부화 · Swagger (Boot 4 함정 포함) 🆕 🐞 🧪

### 배경/문제
① 카카오 토큰 검증을 **타임아웃·에러 구분 없이** 하고 있었고, 실제로 붙는지 미검증이었다. ② 실제 키/비번 넣기 전 **비밀값이 커밋될 위험**. ③ API를 매번 curl/Postman으로 치기 번거로웠다. 모두 Spring Boot **4**라 Boot 3 예제가 안 통하는 함정이 있었다.

### 선정 이유
| 결정 | 이유 |
|---|---|
| 카카오 에러 **4xx=401(토큰무효)/5xx·타임아웃=502(외부장애)** 구분 + 3초 타임아웃 | 사용자 잘못과 카카오 장애를 분리, 무한 대기 방지 |
| 비밀값을 `${ENV:dev기본값}` + `application-local.yml`(gitignore) + config.import | 커밋 파일엔 dev 기본값만, 운영은 env 주입 |
| Swagger = springdoc **3.0.0**(Boot 4 지원) | Boot 4/Jackson 3 호환 버전 |
| dev 소셜 로그인(`@Profile("dev")`) | 카카오 없이 스모크/E2E(운영엔 빈 미생성) |

### 구현/해결 내용
- `KakaoClient` 하드닝(타임아웃·에러 매핑·base URL 설정화), `ExternalApiException`(502). `application.yml` 외부화 + `.gitignore`(application-local.yml·.env·firebase json) + `application-local.yml.example`. `OpenApiConfig`(JWT Authorize). `DevSocialClient`.

### Before / After
| | Before | After |
|---|---|---|
| 카카오 에러 | 모든 실패를 401로 뭉뚱그림, 타임아웃 없음 | 4xx=401 / 5xx·타임아웃=502, 3초 제한 |
| 카카오 실연동 | **미검증** | 실계정 로그인 성공 — `users` 테이블에 실유저(provider=kakao, 닉네임·프로필 포함) |
| API 테스트 | curl/Postman 수동 | `/swagger-ui.html`에서 Authorize 후 버튼 호출 |

### 테스트 / 검증
- 실서버 스모크: 가짜 카카오 토큰 → 실제 kapi.kakao.com → **401 "유효하지 않은 카카오 토큰"**(전 경로 검증). 이후 실계정 토큰으로 **성공 경로 완주**(users 행 생성).
- 비밀값: 더미 `application-local.yml`에 비밀 넣어도 `git status`에 안 뜸 확인.
- **Boot 4 함정(🐞)**: `@DataJpaTest` 등 슬라이스 애노테이션 패키지 재편, `RestClient.Builder` 자동구성 안 됨(정적 팩토리로 생성), starter 이름(`-webmvc`) — 전부 컴파일/부팅으로 확인.

### 남은 과제
Swagger 배포 차단(운영 프로필), 애플 로그인, Flyway 전환(이후 세션에서 완료).

> Git: `e1956e9` 카카오 하드닝+실연동 · `4acb398` 비밀값 외부화 · `2fe4119` dev 소셜 로그인 · `8f178ca` Swagger

---

## 경력기술서용 요약 (백엔드 MVP)

형식: **문제 → 한 행동 → 결과**. (0→1 구축이라 수치형 Before/After 대신 아키텍처·동시성·검증 중심)

- **엔티티는 있으나 컨텍스트가 안 뜨는 상태** → 패키지를 도메인별로 재편하고 컴포넌트 스캔 루트를 바로잡음 + RDB(절친)·Redis(presence) **2단계 조합 쿼리** 설계 → 전 레이어 기동, 핵심 쿼리를 `@DataJpaTest`로 고정.
- **자체 인증이 필요** → 소셜 로그인을 `SocialClient`로 추상화하고 access/refresh **rotation**을 구현, refresh는 Redis TTL → 키 없이 테스트 가능한 구조 + 단일 세션 보안 모델.
- **단위 테스트·코드 리뷰가 동시성 결함 3건 발견** → ① rotation 토큰 중복을 `jti`로, ② rotation 경합을 **Redis Lua 원자화**로, ③ 친구관계 방향성 유니크를 **정규화 쌍 제약**으로 해결 → 재현 테스트로 회귀 고정(검증이 설계 결함을 먼저 잡은 사례).
- **실시간이 본 트랜잭션을 깨거나 유령 알림을 낼 위험** → WebSocket 전파를 **부수효과로 격리**하고 Flash 전파를 **커밋 이후(AFTER_COMMIT)** 로 이동 → 롤백 시 무발신, 전송 실패가 본 작업에 무영향.
- **접속자만 받던 실시간의 사각지대** → `PushSender` 추상화로 **온라인=WebSocket / 오프라인=FCM** 라우팅 → 오프라인 번개 유실 제거, Firebase 키 없이도 기동.
- **외부 연동·비밀·문서의 운영 리스크** → 카카오 에러를 401/502로 구분(+타임아웃), 비밀값을 env/gitignore 구조로 외부화, Swagger(springdoc 3, Boot 4) 도입 → 실계정 로그인 E2E 성공, 비밀 유출 경로 차단.

---

## 수정·생성한 파일 (요약)

**백엔드** (`tuk-server`, 당시 `com.bungae`)
- `global/`: `config`(Security·WebSocket·Jpa Auditing·OpenApi·Firebase·Scheduling), `auth`(jwt·social·token·AuthController/Service), `common`(BaseTimeEntity·Activity), `exception`(BusinessException 계층)
- `user`·`friendship`·`presence`·`flash`·`notification`: 각 `domain`·`repository`·`service`·`controller`(+ flash `scheduler`, presence·flash `ws`)
- `src/test`: 도메인별 `@DataJpaTest`·Mockito 단위 테스트(최종 68개 그린)
- `application.yml`·`docker-compose.yml`·`.gitignore`·`application-local.yml.example`·`build.gradle`(jjwt·firebase-admin·springdoc)
- `docs/API_FOR_APP.md`(앱용 API 계약서), `CLAUDE.md`(설계·배포 체크리스트)

**검증/증거**
- 테스트 68개(단위 + @DataJpaTest), 실서버 스모크(dev·카카오), psql로 생성 유저·제약 확인
- `![TODO]` 1자리(선택): rotation jti 테스트 리포트 캡처 — 없으면 테스트 로그 텍스트로 대체 가능

> 커밋(7/3~7/28): `5cf0cd2`·`2d2a326`·`606d45f`·`109850c`·`21e44dc`·`40755d1`·`734d70c`·`1882660`·`5a6f9c5`·`2ba4e83`·`fcba4ec`·`2fe4119`·`4acb398`·`e1956e9`·`8f178ca`

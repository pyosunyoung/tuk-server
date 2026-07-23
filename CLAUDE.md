# 번개 (bungae) — 프로젝트 컨텍스트

> 코드네임 `bungae`. 정식 제품명은 미정 — 내부 코드·DB·패키지는 계속 `bungae`를 쓴다.

## 무엇을 만드는가
친한 친구들끼리, **거절 걱정 없이 "지금 볼래?"를 띄우는** 즉흥 만남 앱.
- 5~10명 닫힌 절친 그룹 단위. 첫 타겟은 한 대학/동네 (콜드스타트 회피).
- 앱(Flutter) = 실시간·즉흥, 웹(Next.js) = 관리·회고·성장. **웹은 실시간 번개를 다루지 않는다.**

## 절대 지켜야 할 설계 원칙 (코드에 직접 영향)
1. **거절 안 보임** — `FlashRecipient.status`는 `PENDING`/`JOINED` 만. `DECLINED` 절대 추가 금지. "무시/거절" API·이벤트도 만들지 않는다. 무시 = 아무 요청도 안 보냄.
2. **자동 만료** — presence는 Redis TTL로, Flash는 `expires_at` + `@Scheduled` 스위퍼로 만료.
3. **멱등 합류** — `FlashRecipient`에 `UNIQUE(flash_id, user_id)`.
4. **닫힌 절친** — 번개 대상은 항상 서버에서 절친 관계를 재검증한다 (클라이언트가 보낸 대상 그대로 신뢰 금지).

## 기술 스택
- 백엔드: **Spring Boot 4.0.7 / Java 17**, Spring Data JPA, Spring Data Redis, WebSocket(STOMP), Spring Security + JWT(jjwt), OAuth2(소셜 로그인)
- DB: PostgreSQL · 실시간/presence: Redis · 푸시: FCM(firebase-admin)
- 앱: Flutter · 웹: Next.js(React)

> ⚠️ **Boot 4라 Boot 3 예제/블로그와 다름** (실제로 여러 번 부딪힌 부분):
> - 슬라이스 테스트 애노테이션 패키지 재편: `@DataJpaTest` → `org.springframework.boot.data.jpa.test.autoconfigure`, `@AutoConfigureTestDatabase` → `...boot.jdbc.test.autoconfigure`, `TestEntityManager` → `...boot.jpa.test.autoconfigure`
> - starter 이름: `spring-boot-starter-webmvc`(구 `-web`), 테스트는 모듈형(`spring-boot-starter-data-jpa-test` 등)
> - `RestClient.Builder`는 자동 구성 빈이 **아님** → `RestClient.builder()` 정적 팩토리로 직접 생성

## 아키텍처
- 레이어드: Controller → Service → Repository
- 패키지: 도메인별 (`com.bungae.user` / `friendship` / `presence` / `flash` / `notification` / `global`)
- 실시간: 콜 켜기·번개 발송·합류는 REST로 트리거하고, 결과는 WebSocket(STOMP)으로 "절친 중 접속자"에게 전파. 세션 레지스트리 `userId → WS 세션`. 다중 서버 확장 시 Redis Pub/Sub.
- 소셜 로그인: 앱이 SDK로 소셜 토큰 획득 → 백엔드가 검증 → 자체 JWT(access+refresh, rotation) 발급. WebSocket은 CONNECT 헤더의 JWT로 인증.

## 엔티티 (`com.bungae.*.domain`)
User, Friendship(자기참조 N:N, status), Flash(+scheduled_at), FlashRecipient(status: PENDING/JOINED), MeetupRoom(Flash와 1:1), DeviceToken(FCM 토큰), Presence(`@RedisHash` + `@TimeToLive`), BaseTimeEntity, Activity(enum).

## 코딩 컨벤션
- 모든 연관관계 `FetchType.LAZY`
- 상태 변경은 setter가 아니라 도메인 메서드로 (`accept()`, `join()`, `expire()`, `close()`, `setPlace()`)
- 기본 생성자 `protected`, 객체 생성은 `@Builder`
- 테이블명 `users` (user는 예약어). `@EnableJpaAuditing` 필요.

## 구현 순서 (설계 순서 ≠ 구현 순서) — **전 단계 완료**
1. ✅ 프로젝트 셋업 + `application.yml`(PostgreSQL/Redis) + JPA Auditing
2. ✅ Repository + 핵심 쿼리
3. ✅ 인증/소셜 로그인 + JWT
4. ✅ Friendship (절친 추가·수락)
5. ✅ Presence (콜 켜기·조회 + WebSocket)
6. ✅ Flash (발송·합류·모임방 + 만료 스케줄러 + WebSocket)
7. ✅ Notification (FCM)

## 주요 엔드포인트
- 인증: `POST /api/auth/login` · `/refresh` · `/logout`
- 친구: `POST /api/friendships` · `POST /{id}/accept` · `GET /requests` · `GET /api/friendships`
- Presence: `POST/DELETE /api/presence` · `GET /me` · `GET /friends`(지금 한가한 친구)
- 번개: `POST /api/flashes` · `GET /received` · `POST /{id}/join` · `GET /{id}/room` · `PATCH /{id}/room/place`
- 기기토큰: `POST/DELETE /api/device-tokens`
- WebSocket: `/ws` 핸드셰이크(permitAll) → STOMP CONNECT 헤더 JWT 인증. 구독 `/user/queue/presence`, `/user/queue/flash`

## 실시간 알림 라우팅
같은 이벤트를 두 리스너가 분담하며, **둘 다 `@TransactionalEventListener(AFTER_COMMIT)`** (커밋 전 전파 금지 — 롤백 시 유령 알림):
- 접속 중 → WebSocket (`FlashEventBroadcaster`)
- 미접속 → FCM (`FlashPushListener`)
- presence(콜 켜기/끄기)는 **의도적으로 FCM 제외** — 앱 열면 보이는 주변 상태라 푸시하면 시끄러움

## 상세 설계 문서
전체 기획·경쟁분석·기능명세·화면흐름·ERD·시퀀스·API 명세는 Notion 허브 참고:
https://app.notion.com/p/38b90d0318b7817097b4fe985d05fcd6

## 현재 상태
**MVP 백엔드 구현 완료** (구현 순서 ①~⑦). 로컬 기준 전체 테스트 68개 통과, 4대 설계 원칙 코드 반영 확인.

### 아직 안 된 것 (배포 전 필수)
1. ~~비밀값 외부화~~ ✅ **구조 완료** — 모든 환경값이 `${ENV:dev기본값}`. 실제 값은 환경변수 또는 `application-local.yml`(gitignore)로 오버라이드. 커밋 파일엔 로컬 dev 기본값만. 운영 배포 시 실제 값을 env 로 주입만 하면 됨(`application-local.yml.example` 참고). **주의: 실제 카카오 키·운영 비번을 `application.yml`에 직접 쓰지 말 것.**
2. **`ddl-auto: update` → `validate` + Flyway** — 스키마 확정됐으니 마이그레이션으로 전환.
   (주의: `update`는 기존 테이블에 유니크 제약을 새로 안 걸어줌. 제약을 바꾸면 테이블 재생성 필요)
3. **CORS 개방 상태** — `allowedOriginPatterns("*")` + credentials. 배포 시 앱/웹 오리진으로 좁힐 것.
4. **Apple 로그인 미구현** — `SocialClient` 구현체는 Kakao만. Apple은 id_token 공개키 검증 필요.
5. **FCM 실제 키 미연동** — 기본은 `LoggingPushSender`(로그만). `FCM_ENABLED=true` + `FCM_CREDENTIALS_PATH` 주면 실제 발송으로 전환(코드 변경 불필요).
6. **엔드투엔드 미검증** — 실제 카카오 토큰 로그인 → STOMP 연결 → 번개 발송 흐름은 앱 클라이언트 붙일 때 확인 필요.

### 스케일 단계에서 손볼 것 (지금은 MVP라 의도적으로 미적용)
- 만료 스위퍼: 무제한 fetch + 단일 트랜잭션 → 페이징 처리
- 스위퍼가 인스턴스마다 실행 → 다중 서버 시 분산 락(ShedLock)
- WebSocket SimpleBroker(단일 서버) → 다중 서버 시 Redis Pub/Sub·외부 브로커
- FCM 발송이 요청 스레드 동기 실행 → 비동기/큐
- refresh 토큰이 사용자당 1개(단일 세션, 의도적 MVP 선택) → 멀티기기 필요 시 `refresh:{userId}:{deviceId}`로 분리

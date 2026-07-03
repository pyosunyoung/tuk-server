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
- 백엔드: Spring Boot 3, Spring Data JPA, Spring Data Redis, WebSocket(STOMP), Spring Security + JWT, OAuth2(소셜 로그인)
- DB: PostgreSQL · 실시간/presence: Redis · 푸시: FCM
- 앱: Flutter · 웹: Next.js(React)

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

## 구현 순서 (설계 순서 ≠ 구현 순서)
1. 프로젝트 셋업 + `application.yml`(PostgreSQL/Redis 연결) + JPA Auditing
2. **Repository** ← 현재 여기
3. 인증/소셜 로그인 + JWT
4. Friendship (절친 추가·수락)
5. Presence (콜 켜기·조회 + WebSocket)
6. Flash (발송·합류·모임방 + 만료 스케줄러)
7. Notification (FCM)

## 상세 설계 문서
전체 기획·경쟁분석·기능명세·화면흐름·ERD·시퀀스·API 명세는 Notion 허브 참고:
https://app.notion.com/p/38b90d0318b7817097b4fe985d05fcd6

## 현재 상태
설계 완료. 엔티티 코드 작성됨. 다음 할 일: `application.yml` 설정 → Repository + 핵심 쿼리("지금 한가한 친구 조회", "내가 받은 활성 번개").

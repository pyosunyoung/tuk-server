# 툭 (Tuk) — 백엔드

> 친한 친구끼리 **거절 걱정 없이 "지금 볼래?"를 던지는** 즉흥 만남 앱의 서버.
> 앱(Flutter) 리포: [pyosunyoung/tuk-app](https://github.com/pyosunyoung/tuk-app)
> **전체 정리(기획 · 구조 · 부딪힌 문제 · 운영 · 타임라인)**: [tuk-app/docs/OVERVIEW.md](https://github.com/pyosunyoung/tuk-app/blob/main/docs/OVERVIEW.md)

단톡방에 "오늘 저녁 누구 시간 돼?"를 던지고 읽씹당하는 경험에서 시작했습니다.
툭은 **지금 한가한 친한 친구만 보여주고, 한 탭으로 부르고, 거절은 아무에게도 보이지 않게** 만듭니다.

- **운영 중**: Vultr 서울 VM 한 대 · Docker Compose · Caddy 자동 HTTPS (2026-09-15 배포)
- **배포**: `main` 에 push → GitHub Actions 가 테스트 → 이미지 → 배포 → health 확인 → 실패 시 자동 롤백
- **테스트 149개** · Flyway 마이그레이션 V1 ~ V6 · 매일 백업(암호화 외부 사본) · 서버/백업 감시 · 앱 로그 30일 보관
- **실사용**(2026-10-22 ~ 25): 지인 3명과 실제 폰 4대 — 툭 9 · 합류 12 · **받고 합류까지 평균 1~2분** · 그 기간 서버 ERROR 로그 **0**

| 친구 폰 잠금화면에 온 알림 — 새 툭 · 합류 · 약속 시간 / 장소 변경 | |
|---|---|
| <img src="docs/images/real/n_flash.jpg" width="380"> | <img src="docs/images/real/n_joined.jpg" width="380"> |
| <img src="docs/images/real/n_time.jpg" width="380"> | <img src="docs/images/real/n_place.jpg" width="380"> |

화면 전체와 실사용 기록은 앱 리포 README 의 [실사용](https://github.com/pyosunyoung/tuk-app#실사용--지인-3명과-1주-2026-10-22--25) 에.

---

## 이 프로젝트에서 봐주셨으면 하는 것

### 1. 제품 원칙을 코드 구조로 강제했다

| 원칙 | 코드에 어떻게 반영했나 |
|---|---|
| **거절이 보이지 않는다** | `FlashRecipient.status` 를 `PENDING`/`JOINED` **두 개로만** 둔다. `DECLINED` 를 넣는 순간 "누가 씹었는지"가 데이터로 남는다. 받은 친구 신청을 지우는 **"거절" API 도 일부러 만들지 않았다** — 상대의 "보낸 신청"에서 사라져 거절이 드러나기 때문 |
| **시간이 지나면 알아서 꺼진다** | 한가함은 Redis TTL, 툭은 `expires_at` + `@Scheduled` 스위퍼. 단 **합류 마감 ≠ 모임 종료** — 성사된 모임은 약속 시간까지, 시간이 없으면 마감 후 6시간 남긴다. 이 규칙은 `MeetupLifecycle` 한 곳에만 두고 응답의 `visibleUntil` 로 앱에 내려준다 |
| **합류는 멱등하다** | `UNIQUE(flash_id, user_id)`. 재시도로 중복 합류가 생기지 않는다 |
| **대상은 서버가 다시 검증한다** | 클라이언트가 보낸 대상 목록을 믿지 않고 친구 관계를 서버에서 재확인한다 |

### 2. 실시간 알림을 접속 여부로 나눠 보낸다

같은 이벤트를 두 리스너가 나눠 받아 **접속자는 WebSocket(STOMP), 미접속자는 FCM** 으로 보낸다. 둘 다 `@TransactionalEventListener(AFTER_COMMIT)` 이라 롤백되면 유령 알림이 나가지 않는다.
알림 종류: 새 툭 · 합류 · 장소/약속 시간 "정해졌어요 / 바뀌었어요"(정한 본인 제외, 같은 값 재저장은 무시) · 친구 신청/수락. 한가함 켜기는 일부러 푸시하지 않는다(시끄럽다).

### 3. 실제로 운영하며 잡은 것들

| 문제 | 한 일 | 결과 |
|---|---|---|
| CI 첫 실행이 `exit 126` | Windows 커밋은 실행 권한이 안 남는다 → `git update-index --chmod=+x` | 이후 CI 초록 |
| 배포 키가 아무 명령이나 실행 가능 | `authorized_keys` 에 `restrict,command=` 로 **배포 스크립트만** 실행되게 | 셸·scp·포워딩 불가(실검증) |
| SSH 비밀번호 대입이 상시 들어옴 | 비밀번호 로그인 금지, root 는 키로만. cloud-init 설정보다 먼저 읽히게 파일명 `00-` | 비밀번호 실패 하루 **5,855 → 0**(09-15 → 10-14 저널) |
| 초대 코드 무차별 대입 가능 | Redis `INCR` + 첫 실패에만 `EXPIRE` 로 **틀린 시도만** 센다 | 10분 10회 초과 시 429 |
| 탈퇴 시 외래키 위반 위험 | CASCADE 없는 스키마에서 자식 → 부모 벌크 삭제를 한 트랜잭션으로 | 실제 DB 통합 테스트. **삭제 한 줄을 빼면 실패**하는 것까지 확인 |
| 제약 이름이 DB 마다 달라 마이그레이션 실패 | 이름 대신 `pg_constraint` 에서 **내용으로 찾아** 교체(V5) | 테스트 13개 실패 → 137개 통과 |
| 백업이 운영 서버 디스크에만 | `age` 공개 키로 암호화 → Backblaze B2, **복호화 키는 서버에 두지 않음** | B2 에서 받아 임시 DB 복원, 운영과 행 수 일치 |
| 카카오 토큰이 어느 앱에서 발급됐는지 안 봄 | `/v1/user/access_token_info` 의 `app_id` 를 툭 앱과 비교. 설정이 비면 건너뛰고 경고(배포 순서로 로그인이 막히지 않게) | 다른 앱 토큰 401. 비교 줄을 빼면 테스트 실패 |
| 초대 코드 중복을 DB 가 막지 않음 | V6 unique index(NULL 은 여러 개 허용) | 실제 PostgreSQL 테스트 |
| 배포하면 `docker logs` 가 사라짐 | 앱 로그를 볼륨의 파일로(날짜별 · 30일 · 300MB 상한), docker 로그도 10MB × 3 | 실사용 기간 에러 0 을 배포 뒤에도 확인 |

### 4. 실사용 의견으로 고친 것 (1.0.1)

<img src="docs/images/real/feedback_dad.jpg" width="360">

"친구 끊기를 해도 바로 삭제 안 됨" — 끊기는 상대에게 알리지 않으려고 **아무 이벤트도 보내지 않았다.** 그래서 끊긴 쪽은 앱을 다시 열 때까지 끊은 사람을 친구 목록 · 지금 한가한 친구 · 툭 보낼 대상에서 계속 봤다(보낸 신청 취소도 같아서, 받은 쪽이 수락하면 404).

- `FriendsChangedEvent` 를 끊기(상대) · 신청 취소(받은 쪽)에서 발행 — 알림용 `FriendshipEvent` 와 **분리**
- `FriendsChangedBroadcaster`(AFTER_COMMIT): 상대가 접속 중일 때만 `/user/queue/friends` 로 `{"type":"FRIENDS_CHANGED"}` — 화면에 아무것도 띄우지 않는 **데이터 신호**라 FCM 은 쓰지 않는다(꺼져 있으면 다음 실행 때 새로 불러온다)
- 옛 앱(1.0.0)은 이 주소를 구독하지 않아 서버를 먼저 배포해도 안전 → 앱 1.0.1 은 `public/app-version.json` 한 줄로 안내

---

## 기술 스택

- **Spring Boot 4.0.7 / Java 17**, Spring Data JPA, Spring Security + JWT(jjwt)
- **PostgreSQL** (도메인) · **Redis** (한가함 TTL, refresh 토큰, 세션 레지스트리, 시도 횟수 제한)
- **WebSocket(STOMP)** 실시간 · **FCM** 푸시 · **카카오** 로그인 / 로컬(장소 검색) API
- **Flyway** · **Docker Compose + Caddy** · **GitHub Actions + GHCR**
- 운영 보조: Backblaze B2(백업 사본) · UptimeRobot(서버 감시) · healthchecks.io(백업 감시)

## 아키텍처

```mermaid
flowchart LR
    App[Flutter 앱] -->|REST + JWT| Caddy
    App <-->|STOMP over WebSocket| Caddy
    Caddy[Caddy · HTTPS 자동 발급] --> Server[Spring Boot]
    Caddy -->|정적 페이지| Public[약관 · 초대 · 공유 카드 · app-version.json]
    Server --> PG[(PostgreSQL)]
    Server --> Redis[(Redis)]
    Server -->|미접속자| FCM[FCM] --> App
    Server -->|토큰 검증 · 장소 검색| Kakao[카카오 API]
    PG -. 매일 04:00 pg_dump → age 암호화 .-> B2[(Backblaze B2)]
```

요청 흐름은 **Controller → Service → Repository**, 패키지는 **도메인별**(`user` / `friendship` / `presence` / `flash` / `notification` / `place` / `global`).

**인증**: 앱이 카카오 SDK 로 받은 토큰을 서버가 카카오에 검증 → 자체 JWT(access 30분 + refresh 14일). 검증할 때 그 토큰이 **툭 앱에서 발급된 것인지(app_id)** 먼저 확인한다 — 다른 카카오 앱의 토큰으로 로그인되는 것(토큰 치환)을 막기 위해. refresh 는 **Redis Lua 로 원자적 rotation** 이라 같은 토큰으로 동시에 와도 하나만 성공한다. WebSocket 은 CONNECT 헤더의 JWT 로 인증.

**장소 검색**은 서버가 카카오 로컬 API 를 대신 호출한다 — REST 키를 앱에 넣으면 APK 에서 누구나 꺼내 쓸 수 있어서.

## 주요 엔드포인트

| 도메인 | 엔드포인트 |
|---|---|
| 인증 | `POST /api/auth/login` · `/refresh` · `/logout` |
| 내 정보 | `GET /api/users/me` · `DELETE /api/users/me`(탈퇴) |
| 친구 | `GET /api/friendships` · `GET /invite-code` · `POST /redeem` · `POST /{id}/accept` · `GET /requests` · `GET /requests/sent` · `DELETE /requests/{id}`(보낸 신청 취소) · `DELETE /friends/{userId}`(끊기) |
| 한가함 | `POST`/`DELETE /api/presence` · `GET /me` · `GET /friends` |
| 툭 | `POST /api/flashes` · `GET /received` · `GET /sent` · `POST /{id}/join` · `GET /{id}/room` · `PATCH /{id}/room/place` · `PATCH /{id}/room/time` |
| 장소 | `GET /api/places/search?query=` |
| 기기 토큰 | `POST`/`DELETE /api/device-tokens` |
| 실시간 | `/ws` → 구독 `/user/queue/presence`, `/user/queue/flash`, `/user/queue/friends`(친구 관계 변경 신호) |

요청·응답 형태와 이벤트 목록은 [docs/API_FOR_APP.md](docs/API_FOR_APP.md)(앱과의 계약서).

---

## 실행

### 로컬 개발

```bash
docker compose up -d      # PostgreSQL + Redis
./gradlew bootRun         # 기본 프로필 dev
```

dev 프로필에선 `DevSocialClient` 로 카카오 없이 로그인된다(운영에선 401):

```bash
curl -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"provider":"dev","token":"alice"}'
```

### 운영 배포

`main` 에 push 하면 끝이다(문서만 바뀐 커밋은 건너뜀). 서버에서 빌드하지 않는다 — 2GB VM 에서 Gradle 이 돌면 앱과 메모리를 다툰다.

```
test(PG·Redis 서비스 컨테이너) → image(GHCR) → deploy(SSH 강제 명령) → health 180초 대기 → 실패 시 직전 이미지로 롤백
```

> **`TZ=Asia/Seoul` 이 중요하다.** 만료가 서버 벽시계 기준이라 UTC 컨테이너면 9시간 어긋난다.

운영 절차(롤백 · 백업 복구 · 로그 보는 명령)는 [CLAUDE.md](CLAUDE.md) "운영 기동 방법" · "앱 로그".

## 테스트

```bash
./gradlew test    # 149개(동시성 테스트 20회 반복 포함 실행 168회)
```

서비스는 Mockito, 레포지토리·탈퇴·마이그레이션은 **실제 PostgreSQL**(`@DataJpaTest` / `@SpringBootTest`)로 검증한다. 제약조건·네이티브 쿼리를 H2 로 대신하면 실제와 달라져서다.
refresh rotation 의 원자성도 **실제 Redis 에 같은 토큰으로 동시에 요청**해 교체가 정확히 한 번만 되는지 본다(Lua 대신 읽기→비교→쓰기로 바꾸면 8요청에 토큰 8개가 발급돼 실패하는 것까지 확인).

## 문서

| 문서 | 내용 |
|---|---|
| [docs/FEATURE_NOTES_2026-07-03_07-28_backend.md](docs/FEATURE_NOTES_2026-07-03_07-28_backend.md) | 백엔드 MVP 0 → 1 (인증 · 친구 · 한가함 · 툭 · 실시간 · FCM) |
| [docs/FEATURE_NOTES_2026-07-30_08-27_backend.md](docs/FEATURE_NOTES_2026-07-30_08-27_backend.md) | 앱 API 계약서 · 초대코드 · 내가 던진 툭 조회 |
| [docs/OPS_NOTES_2026-09.md](docs/OPS_NOTES_2026-09.md) | 배포 준비 — Flyway · 운영 프로필 · 배포처 선정 · Docker · Caddy |
| 앱 리포 [`docs/FEATURE_NOTES_*`](https://github.com/pyosunyoung/tuk-app/tree/main/docs) | 09월 이후 기능과 운영(배포 · CI/CD · 백업 · 모니터링)을 기간별로 |
| [docs/API_FOR_APP.md](docs/API_FOR_APP.md) | 앱과의 API 계약서 |
| [CLAUDE.md](CLAUDE.md) | 설계 원칙, 운영 절차, 배포 체크리스트 |

각 정리본은 **배경 → 선정 이유 → 구현 → Before/After(캡처·수치) → 테스트 → 남은 과제** 순서로 썼다.

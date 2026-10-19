# 툭 (Tuk) — 프로젝트 컨텍스트

> 제품명 **툭(Tuk)** 확정. 코드·DB·패키지도 `tuk` 로 통일했다(구 코드네임 `bungae`).
> 문서에 나오는 "번개"는 제품명이 아니라 **기능 이름**(Flash — 친구들에게 띄우는 즉흥 초대)이다.

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
- 패키지: 도메인별 (`com.tuk.user` / `friendship` / `presence` / `flash` / `notification` / `global`)
- 실시간: 콜 켜기·번개 발송·합류는 REST로 트리거하고, 결과는 WebSocket(STOMP)으로 "절친 중 접속자"에게 전파. 세션 레지스트리 `userId → WS 세션`. 다중 서버 확장 시 Redis Pub/Sub.
- 소셜 로그인: 앱이 SDK로 소셜 토큰 획득 → 백엔드가 검증 → 자체 JWT(access+refresh, rotation) 발급. WebSocket은 CONNECT 헤더의 JWT로 인증.

## 엔티티 (`com.tuk.*.domain`)
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
- 내 정보: `GET /api/users/me` (앱이 '나'를 판별하는 기준: 모임방 members 표시·프로필 아바타)
- 친구: `POST /api/friendships` · `POST /{id}/accept` · `GET /requests` · `GET /api/friendships`
- Presence: `POST/DELETE /api/presence` · `GET /me` · `GET /friends`(지금 한가한 친구)
- 번개: `POST /api/flashes` · `GET /received` · `POST /{id}/join` · `GET /{id}/room` · `PATCH /{id}/room/place`
- 기기토큰: `POST/DELETE /api/device-tokens`
- 장소 검색: `GET /api/places/search?query=` (카카오 로컬 API 프록시 — REST 키는 서버에만)
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
**MVP 백엔드 구현 완료** (구현 순서 ①~⑦). 로컬 기준 전체 테스트 82개 통과, 4대 설계 원칙 코드 반영 확인.
앱(Flutter)까지 붙여 카카오 로그인·친구·presence·번개·STOMP 실시간 전 구간 실검증 완료.

### 아직 안 된 것 (배포 전 필수)
0. **기본 활성 프로필이 `dev`** (`application.yml`의 `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}`) — 로컬 편의를 위해 어떤 방식으로 켜도 dev 로그인이 되게 함. **운영 배포 시 반드시 `SPRING_PROFILES_ACTIVE=prod`(등)로 덮을 것.** dev 프로필은 `DevSocialClient`(아무 문자열로 로그인)를 열기 때문에 운영에서 켜지면 보안 구멍이다.
1. ~~비밀값 외부화~~ ✅ **구조 완료** — 모든 환경값이 `${ENV:dev기본값}`. 실제 값은 환경변수 또는 `application-local.yml`(gitignore)로 오버라이드. 커밋 파일엔 로컬 dev 기본값만. 운영 배포 시 실제 값을 env 로 주입만 하면 됨(`application-local.yml.example` 참고). **주의: 실제 카카오 키·운영 비번을 `application.yml`에 직접 쓰지 말 것.**
2. ~~`ddl-auto: update` → `validate` + Flyway~~ ✅ **완료** — 스키마의 진실원은 이제 `src/main/resources/db/migration`.
   `V1__init.sql` 이 지금까지의 스키마 baseline. **앞으로 엔티티만 고치면 기동이 실패한다** — 반드시 V2, V3... 추가할 것.
   기존 개발 DB 는 `baseline-on-migrate: true` 로 V1 을 건너뛰고, 빈 DB(운영)는 V1 부터 적용된다(둘 다 실검증).
   ⚠️ Boot 4 는 자동구성이 모듈로 쪼개져서 **`org.springframework.boot:spring-boot-flyway` 가 있어야** 마이그레이션이 돈다
   (`flyway-core` 만 넣으면 클래스패스에만 있고 조용히 아무것도 안 함 — 실제로 겪음).
3. ~~CORS 개방 상태~~ ✅ **완료** — `app.cors.allowed-origins`(기본 `http://localhost:3000`)에서 오리진을 읽는다.
   운영은 `CORS_ALLOWED_ORIGINS` 로 실제 도메인 주입. `allowCredentials=true` 라 와일드카드는 못 쓴다(스펙상 금지).
4. **Apple 로그인 미구현** — `SocialClient` 구현체는 Kakao만. Apple은 id_token 공개키 검증 필요.
5. **FCM 실제 키 미연동** — 기본은 `LoggingPushSender`(로그만). `FCM_ENABLED=true` + `FCM_CREDENTIALS_PATH` 주면 실제 발송으로 전환(코드 변경 불필요).
6. ~~Swagger UI 개발용 개방~~ ✅ **완료** — `application-prod.yml` 에서 `springdoc.*.enabled=false`,
   그리고 `SecurityConfig` 가 그 값을 보고 **permitAll 등록 자체를 건너뛴다**(운영에서 401).
7. ~~엔드투엔드 검증~~ ✅ **완료** — 카카오 로그인(계정·카카오톡 앱 양쪽)·친구·presence·번개 REST + STOMP 실시간까지
   에뮬레이터와 실기기(갤럭시 S25)로 검증.
8. **헬스체크** ✅ **완료** — actuator 추가, `health` 만 노출(`show-details: never`). 나머지 actuator 엔드포인트는 401.

### 운영 기동 방법 (Docker)
VM 한 대에 앱 + PostgreSQL + Redis 를 통째로 올리는 구성.

**배포 = `main` 에 push.** GitHub Actions(`.github/workflows/backend.yml`)가 한다:
`test`(PG·Redis 서비스 컨테이너로 전체 테스트) → `image`(GHCR `ghcr.io/pyosunyoung/tuk-server:<커밋SHA>` push) → `deploy`(SSH → 서버의 `ops/deploy/tuk-deploy`) → 공개 URL health 스모크 테스트.
- **서버에서 빌드하지 않는다** — 2GB VM 에서 Gradle 빌드가 돌아가는 앱과 메모리를 다툰다.
- 서버 `deploy` 사용자 + 전용 키. authorized_keys 가 `restrict,command="/usr/local/bin/tuk-deploy"` 라 그 키로는 배포 스크립트만 실행된다(셸·scp·포워딩 불가 — 실검증).
  스크립트: 인자 검증(`deploy <sha> <actor>`) → stdin 1행 GHCR 임시토큰 + tar(compose·Caddyfile) → pull → `up -d` → health 대기(180초) → **실패 시 직전 태그로 자동 롤백**. 성공 태그는 `/opt/tuk-server/.deploy-tag`, 현재 태그는 `.env` 의 `TUK_IMAGE_TAG`(스크립트가 관리).
  스크립트를 고치면 서버 `/usr/local/bin/tuk-deploy` 에도 root 로 복사해야 반영된다(자기 자신을 배포하지 않음).
- Secrets: `DEPLOY_HOST` `DEPLOY_USER` `DEPLOY_SSH_KEY` `DEPLOY_KNOWN_HOSTS`(ED25519 한 줄). GHCR 은 실행마다 발급되는 `GITHUB_TOKEN` — 영구 토큰 없음.
- **롤백(수동)**: Actions 에서 이전 성공 커밋의 워크플로를 Re-run 하거나, 서버에서 `.env` 의 `TUK_IMAGE_TAG` 를 이전 SHA 로 바꾸고 `docker compose -f docker-compose.prod.yml up -d`(GHCR 로그인 필요).
- 배포 순간 앱 재시작으로 **약 40초 502** — 무중단 배포는 미적용.
- ⚠️ **Windows 에서 커밋하면 실행 권한이 기록되지 않는다**(`core.filemode=false`) — 새 셸 스크립트는 `git update-index --chmod=+x <파일>` 필수. 첫 CI 가 `./gradlew` exit 126 으로 실패했던 원인(Dockerfile 의 `chmod +x` 가 가리고 있었음).
- 배포 키 문제 진단: deploy job 의 Prepare SSH 가 개인키 여부(`ssh-keygen -y`)를 검사하고 지문을 찍는다. 서버 쪽은 `journalctl _COMM=sshd | grep "for deploy"` 로 GitHub 러너 접속·지문 확인. 키 교체 = 새 키 생성 → 서버 `/home/deploy/.ssh/authorized_keys` 한 줄 교체 → Secret `DEPLOY_SSH_KEY` 갱신.
- 서버 설정 파일(`.env`, `firebase-service-account.json`)은 리포에 없고 서버 `/opt/tuk-server` 에만 있다. 새 서버 구축 시 `.env.example` 참고해 수동 작성.

구성: **Caddy(80/443) → app(8080) → postgres·redis**. 앱·DB·Redis 는 외부에 포트를 열지 않는다.
Caddy 가 `TUK_DOMAIN` 으로 Let's Encrypt 인증서를 자동 발급·갱신한다
(발급 조건: 그 도메인의 A 레코드가 서버 공인 IP 를 가리킬 것).
**안드로이드는 평문 HTTP 를 기본 차단하므로 배포 앱에는 HTTPS 가 필수다.**

- **`TZ=Asia/Seoul` 필수** — 번개 만료가 서버 벽시계(LocalDateTime) 기준이라 UTC 컨테이너면 9시간 어긋난다. Dockerfile·compose 양쪽에 박아뒀다.
- DB·Redis 는 포트를 외부에 열지 않는다(`expose` 만). 앱만 내부 네트워크로 붙는다.
- Redis 는 AOF 켜둔다 — refresh 토큰이 여기 있어서 끄면 재시작 시 전원 재로그인.
- 서비스 계정 키는 **이미지에 굽지 않고** 볼륨으로 주입한다(`.dockerignore` 로도 차단).
- **카카오 로그인 발급 앱 확인(2026-10-19~)**: `.env` 의 `KAKAO_APP_ID` = **"툭" 앱**의 카카오 앱 ID(숫자, 장소 검색용 "공유캘린더" 앱 아님). 비어 있으면 확인을 건너뛰고 기동 시 경고(`kakao.app-id 미설정`). 값을 넣은 뒤 `docker compose -f docker-compose.prod.yml up -d app` 로 재생성해야 반영.
- 직접 실행하려면: `SPRING_PROFILES_ACTIVE=prod` + `DB_URL` `DB_USERNAME` `DB_PASSWORD` `REDIS_HOST` `REDIS_PORT` `JWT_SECRET`(32바이트+) `CORS_ALLOWED_ORIGINS` (선택 `FCM_ENABLED` `FCM_CREDENTIALS_PATH`)

운영 프로필 검증 완료: 헬스 200 / Swagger·dev로그인·다른 actuator 401 / 허용 오리진만 CORS 통과.
**배포 완료(2026-09-15)**: Vultr 서울 `158.247.230.36`, 코드 위치 `/opt/tuk-server`, 도메인 `tuk.158-247-230-36.sslip.io`(임시 — 진짜 도메인 사면 `.env` 의 `TUK_DOMAIN`·`CORS_ALLOWED_ORIGINS` 만 교체).
첫 배포는 수동(`git archive` 전송 + 서버 빌드)이었고, 2026-09-16 부터 위 CI/CD 로 전환.
외부 검증: health 200 / HTTP→HTTPS 308 / Swagger·actuator·dev로그인 401 / 타 오리진 CORS 403 / `/ws` 101 / 8080 외부 차단. 이미지 빌드 약 4분, 앱 기동 약 40초.

### 서버 SSH 강화 (2026-09-17~)
- `ops/ssh/00-tuk-hardening.conf` → 서버 `/etc/ssh/sshd_config.d/`. **비밀번호 로그인 전면 금지**, root 는 키로만(`prohibit-password`), `MaxAuthTries 3`.
- 적용 전 24시간 로그인 실패 **6,454회**(한 IP 3,248회) 관측 — 비밀번호 대입 공격이 상시 들어온다.
- ⚠️ sshd 는 같은 옵션이 여러 번 나오면 **먼저 읽은 값**을 쓴다. Vultr 의 `50-cloud-init.conf` 가 `PasswordAuthentication yes` 라서 파일명을 `00-` 으로 시작해야 이긴다. 확인은 `sshd -T | grep passwordauthentication`.
- 변경 시 안전장치: `systemd-run --on-active=10min` 으로 자동 원상복구 타이머를 먼저 걸고 → `sshd -t` → `systemctl reload ssh`(기존 세션 유지) → 새 접속으로 확인 후 타이머 취소.
- 키를 잃어버리면 SSH 로는 못 들어간다 → **Vultr 웹 콘솔**(서버 화면에 직접 접속, sshd 설정과 무관)로 root 비밀번호 로그인해 복구.

### 장소 검색 · 카카오 REST API 키 (2026-09-18~)
- `PATCH /room/place` 가 `kakaoPlaceId`·`placeAddress` 를 선택으로 받는다(Flyway **V2**, CI/CD 로 나간 첫 스키마 변경). 장소는 **통째로 교체** — 직접 입력으로 바꾸면 이전 가게의 ID·주소·좌표가 null 로 지워진다.
- ⚠️ **키는 '툭' 앱이 아니라 '공유캘린더' 앱의 REST API 키(`tuk-server`)** 다. 카카오맵(로컬) API 무료 쿼터는 계정당 첫 앱에만 주어지는데 그게 공유캘린더라, 툭 앱에서 켜려면 유료(월렛 연결)였다. 로그인(툭 네이티브 키)과는 무관.
  → **공유캘린더 앱을 삭제하거나 그 앱의 카카오맵 사용 설정을 끄면 장소 검색이 멈춘다**(앱은 '그대로 쓰기'로 대체되어 고장나진 않음).
- 키 위치: 로컬 `application-local.yml` 의 `kakao.rest-api-key`, 운영 `/opt/tuk-server/.env` 의 `KAKAO_REST_API_KEY`. 비어 있으면 검색만 502.
- 카카오가 401/403 을 주면 `KakaoLocalClient` 가 경고 로그를 남긴다. 403 `disabled OPEN_MAP_AND_LOCAL service` = 콘솔에서 그 앱의 카카오맵 사용 설정이 꺼짐.
- 로컬에서 한글로 테스트할 땐 Git Bash `curl` 금지(CP949 로 넘어가 서버가 거부) — Python `urllib` 로.
- 알려진 사소한 점: 인코딩이 깨진 요청은 `/error` 가 인증 필요라 400 대신 401 로 응답한다.

### 카카오톡 공유 카드용 정적 파일 (2026-09-18~)
- 리포 `public/` → 배포 시 서버 `/opt/tuk-server/public` → Caddy 컨테이너 `/srv/public`(폴더 연결).
  `/share/tuk-card.png`(카드 이미지 800×400), `/invite?code=`(앱 없는 친구용 초대 안내), `/app`(앱 없는 사람용 모임방 안내). 나머지 경로는 전부 앱으로 reverse_proxy.
- 배포 스크립트가 `public/` 을 `rsync --delete` 로 **폴더 안만** 맞춘다(폴더를 바꿔치면 폴더 연결이 옛 폴더를 봄 — Caddyfile 단일 파일과 같은 함정).
- 카드 링크가 동작하려면 카카오 콘솔(**툭 앱**)의 사이트 도메인에 이 도메인과 `https://place.map.kakao.com` 이 등록돼 있어야 한다.

### DB 백업 (2026-09-16~)
- **매일 04:00 KST** systemd 타이머가 `pg_dump -Fc` → `/var/backups/tuk/tuk-YYYYMMDD-HHMM.dump`(600), **14일 보관**. 파일은 `ops/backup/` 에 있다(서버 재구축 시 `/usr/local/bin`·`/etc/systemd/system` 에 복사 후 `systemctl enable --now tuk-db-backup.timer`).
- **외부 사본(2026-10-07~)**: 같은 스크립트가 덤프를 **age 로 암호화**해 Backblaze B2 비공개 버킷에 올린다(`tuk-YYYYMMDD-HHMM.dump.age`, **30일 보관**, 업로드 후 크기 대조). 설정은 서버 `/etc/tuk/backup-offsite.env`(root 600: `B2_BUCKET`·`B2_KEY_ID`·`B2_APP_KEY`·`AGE_RECIPIENT`). 이 파일이 없으면 서버 디스크에만 저장.
  - 서버에는 **공개 키만** 있다(암호화만 가능). **복호화 개인 키(`AGE-SECRET-KEY-…`)는 서버에 두지 않고 운영자가 따로 보관** — 서버를 잃어도 B2 사본을 풀 수 있게, 서버가 털려도 지난 백업은 못 풀게.
  - 서버에 `rclone`·`age` 필요(`apt install -y rclone age`).
  - B2 에서 복구: `rclone copyto b2:<버킷>/<파일>.dump.age x.dump.age`(env 변수 설정 후) → `age -d -i <개인키파일> -o x.dump x.dump.age` → 아래 복구 절차.
- **백업 감시**: env 에 `HC_PING_URL`(healthchecks.io)이 있으면 성공 시 그 주소, 실패 시 `/fail` 을 부른다 → 실패는 즉시, 아예 안 돌면 하루+유예 뒤 메일.
- **서버 감시**: UptimeRobot 이 5분마다 `/actuator/health` 를 확인(2026-10-07~, 운영자 메일로 알림). 배포 중 잠깐의 502 로 Down/Up 알림이 올 수 있다.
- Redis 는 백업 안 함(refresh 토큰·콜 상태뿐 — 잃으면 재로그인).
- 상태 확인: `systemctl list-timers tuk-db-backup.timer` · `journalctl -u tuk-db-backup.service`
- 수동 실행: `systemctl start tuk-db-backup.service`
- **복구**(검증됨 — 임시 DB 에 복원해 운영과 행 수·Flyway 버전 일치 확인):
  ```bash
  docker compose -f docker-compose.prod.yml stop app
  docker exec tuk-postgres dropdb -U postgres tuk && docker exec tuk-postgres createdb -U postgres tuk
  docker exec -i tuk-postgres pg_restore -U postgres -d tuk --no-owner < /var/backups/tuk/<파일>.dump
  docker compose -f docker-compose.prod.yml start app
  ```

### 스케일 단계에서 손볼 것 (지금은 MVP라 의도적으로 미적용)
- 만료 스위퍼: 무제한 fetch + 단일 트랜잭션 → 페이징 처리
- 스위퍼가 인스턴스마다 실행 → 다중 서버 시 분산 락(ShedLock)
- WebSocket SimpleBroker(단일 서버) → 다중 서버 시 Redis Pub/Sub·외부 브로커
- FCM 발송이 요청 스레드 동기 실행 → 비동기/큐
- refresh 토큰이 사용자당 1개(단일 세션, 의도적 MVP 선택) → 멀티기기 필요 시 `refresh:{userId}:{deviceId}`로 분리

# 운영 정리본 — 2026-09-02 ~ 09-10 (배포 준비)

로컬에서만 돌던 백엔드를 **인터넷에 내놓아도 되는 상태로 만들고(Flyway · 운영 프로필), 어디에 어떻게 올릴지 정한(배포처 · Docker · Caddy)** 작업을 기능 단위로 정리한다.
실제 배포(09-15)와 그 뒤의 CI/CD · SSH 강화 · 백업은 앱 리포 [`docs/FEATURE_NOTES_2026-09-15_09-18.md`](https://github.com/pyosunyoung/tuk-app/blob/main/docs/FEATURE_NOTES_2026-09-15_09-18.md)에 있다.
같은 기간 앱 쪽 작업(카카오 로그인 · `/users/me` · FCM · 아이콘)은 [`docs/FEATURE_NOTES_2026-08-30_09-10.md`](https://github.com/pyosunyoung/tuk-app/blob/main/docs/FEATURE_NOTES_2026-08-30_09-10.md).
운영 절차(배포 · 롤백 · 복구 명령)는 [`CLAUDE.md`](../CLAUDE.md) "운영 기동 방법"이 기준이고, 이 문서는 **왜 그렇게 정했는지**를 남긴다.

- 범위: 서버 `tuk-server` 5커밋(`09c138f` · `acc27e3` · `c2b4f03` · `a263e42` · `12e2b1c`) + 배포처 선정(커밋 없음, 09-02 ~ 09-10 대화 기록)
- 테스트: 82개 그대로(이 기간 변경은 설정 · 빌드 파일이라 테스트 대신 **기동 · HTTP 응답으로 검증**)
- 캡처: 화면이 없는 작업이라 **터미널 출력**으로 대신한다. 당시 결과는 커밋 메시지에 남긴 값, `10-15 재확인` 표시가 붙은 출력은 2026-10-15 에 다시 실행한 것(읽기 전용 명령만)
- **확인 필요** 는 기록이 남아 있지 않아 지어내지 않은 부분

> 분류 표기: 🆕 신규 기능 · 🧭 설계/선정 · ⚙️ 개선 · 🐞 오류 해결 · 🧪 테스트

---

## 1. Flyway 도입 — 스키마를 Hibernate 대신 SQL 파일이 관리 ⚙️ 🧭 🧪

### 배경/문제
`ddl-auto: update` 였다. 앱이 뜰 때 Hibernate 가 엔티티와 DB 를 비교해 **스스로 판단해서 `ALTER TABLE` 을 날렸다.** 전날(09-01) `placeSetBy` 필드를 추가했을 때 아무것도 안 했는데 `place_set_by` 컬럼이 생긴 게 그 결과였다. 운영에선 세 가지가 문제다.
- **추가만 하고 변경 · 삭제는 안 한다.** 친구관계 유니크 제약을 바꿨을 때 기존 테이블엔 안 걸렸던 일을 이미 겪었다 — 개발 DB 와 운영 DB 가 조용히 달라진다
- 운영 DB 에 어떤 DDL 이 나갈지 **배포 전에 아무도 모른다**
- 지금 DB 가 어느 버전인지 **이력이 없다**

첫 배포는 빈 DB 에 테이블을 만드는 일이다. 그걸 `update` 에 맡기면 "Hibernate 가 알아서 만든 스키마"가 운영 DB 가 되고, 두 번째 배포부터 그걸 어떻게 바꿀지 근거가 없다.

### 선정 이유
| 결정 | 고려한 것 | 고른 이유 |
|---|---|---|
| `validate` + Flyway | `update` 유지 / Liquibase | 엔티티와 DB 가 어긋나면 **기동 자체를 실패**시킨다. 어긋난 채로 떠서 사용자가 번개를 띄우는 순간 500 이 나는 것보다 배포 시점에 멈추는 게 낫다. 마이그레이션은 읽을 수 있는 SQL 파일이면 충분 |
| V1 = **실제 스키마를 `pg_dump -s` 로 떠서** 정리 | 엔티티 보고 손으로 작성 | 지금까지 Hibernate 가 실제로 만든 것과 V1 이 정확히 같아야 기존 DB 와 새 DB 가 같아진다. 랜덤 제약 이름(`uk...`)만 읽을 수 있는 이름으로 바꿨다 |
| 개발 DB 는 `baseline-on-migrate: true` | 개발 DB 를 지우고 새로 생성 | 이미 테이블이 있는 개발 DB 는 V1 을 건너뛰고 "V1 까지 적용됨"으로 기록 → **데이터 보존** |
| 운영은 `baseline-on-migrate: false`(§2) | 같은 설정 공유 | 운영 DB 는 V1 부터 만든다. 스키마가 어긋난 DB 를 조용히 건너뛰는 쪽이 더 위험하다 |

### 구현/해결 내용 (`09c138f`)
```diff
   jpa:
     hibernate:
-      ddl-auto: update
+      ddl-auto: validate
+  flyway:
+    enabled: true
+    baseline-on-migrate: true
+    baseline-version: 1
```
`src/main/resources/db/migration/V1__init.sql`(98줄) 추가.

### 오류 해결 🐞 — Boot 4 에선 `flyway-core` 만으로는 아무 일도 안 일어난다
처음엔 `flyway-core` 만 넣었다. 클래스패스엔 있는데 **마이그레이션이 돌지 않았고, 로그조차 없었다.** Spring Boot 4 는 자동 구성이 모듈로 쪼개져서 `spring-boot-flyway` 가 따로 있어야 한다(Boot 3 예제 · 블로그와 다른 지점).
```gradle
implementation 'org.springframework.boot:spring-boot-flyway'   // Boot 4: 이게 있어야 자동 구성이 돈다
implementation 'org.flywaydb:flyway-core'
runtimeOnly 'org.flywaydb:flyway-database-postgresql'
```

### Before / After
| | Before | After |
|---|---|---|
| 스키마 변경 | 엔티티만 고치면 Hibernate 가 알아서 | **마이그레이션 파일을 같이 써야** 기동된다 |
| 이력 | 없음 | `flyway_schema_history` |
| 엔티티 ↔ DB 불일치 | 그대로 뜬다 | 기동 실패 |

개발 DB 의 이력 — 09-02 에 **V1 은 BASELINE 으로 건너뛰고**, 그 뒤 스키마 변경은 전부 SQL 파일로 들어갔다(10-15 재확인, 로컬 DB):
```text
 installed_rank | version |        description        |   type   |              script               | installed_on
----------------+---------+---------------------------+----------+-----------------------------------+--------------
              1 | 1       | << Flyway Baseline >>     | BASELINE | << Flyway Baseline >>             | 2026-09-02
              2 | 2       | meetup room place detail  | SQL      | V2__meetup_room_place_detail.sql  | 2026-09-18
              3 | 3       | meetup room meet at       | SQL      | V3__meetup_room_meet_at.sql       | 2026-09-24
              4 | 4       | meetup room changed flags | SQL      | V4__meetup_room_changed_flags.sql | 2026-10-01
              5 | 5       | activity pc room          | SQL      | V5__activity_pc_room.sql          | 2026-10-02
```
운영 DB 는 빈 DB 에서 V1 부터 적용됐어야 한다. 운영 DB 의 이력을 직접 조회한 기록은 없다 — **확인 필요**(서버에서 `docker exec tuk-postgres psql -U postgres -d tuk -c "select version, type, installed_on from flyway_schema_history"`).

### 테스트 🧪
- **기존 개발 DB**: 기동 → baseline 기록, 데이터 보존(users 13)
- **빈 DB**: 기동 → V1 적용 → `validate` 통과 → 기동 성공
- 두 경우 모두 09-02 에 직접 확인(커밋 `09c138f` 메시지)

### 남은 과제
- 이후 V2 ~ V5 가 실제로 이 경로로 운영에 나갔다(V2 는 CI/CD 로 나간 첫 스키마 변경, V5 는 제약 이름 함정 — 앱 리포 FEATURE_NOTES_2026-09-30_10-07 §25)
- 09-16 부터 CI 가 PostgreSQL 서비스 컨테이너로 전체 테스트를 돌린다. 그 과정에서 빈 DB 에 V1 ~ 최신이 전부 적용되는지는 이 문서에서 확인하지 않았다 — **확인 필요**

---

## 2. 운영 프로필 — Swagger 차단 · CORS 좁히기 · 헬스체크 ⚙️ 🐞 🧪

### 배경/문제
로컬에서 편하려고 열어 둔 것들이 공개되는 순간 구멍이 된다.
- **Swagger**(`/v3/api-docs`, `/swagger-ui`): 모든 엔드포인트 · 요청/응답 필드 · 인증 요구 여부를 통째로 공개한다. dev 프로필이 켜진 채 나가면 "아무 문자열로 로그인되는 provider 가 있다"는 사실까지 문서에 적혀 있는 셈이다
- **CORS** `allowedOriginPatterns("*")` + `allowCredentials(true)`: 아무 웹사이트나 허용하는 구성. 사실 이 조합은 스펙상 금지라 브라우저가 거부하기도 하는, 애매하게 망가진 상태였다
- 그리고 작업 중 발견: **`/actuator/health` 가 401** 이었다. `permitAll` 줄은 있는데 actuator 의존성이 없어서 그 줄이 죽은 설정이었다. 배포했다면 헬스 프로브가 바로 실패했을 상태

### 선정 이유
| 결정 | 이유 |
|---|---|
| **프로필로 분리**(`application-prod.yml`) | 개발 편의 ↔ 운영 안전은 하나를 고르는 문제가 아니다. dev 에선 Swagger · dev 로그인이 그대로 돼야 한다 — 보안을 조이면서 개발 흐름을 망가뜨리면 그것도 실패 |
| Swagger 경로의 **`permitAll` 등록 자체를 건너뜀** | 문서 기능만 끄고 경로는 열려 있는 어중간한 상태를 원천 차단. `SecurityConfig` 가 `springdoc.api-docs.enabled` 값을 직접 읽는다 |
| CORS 허용 목록을 **설정값으로** | 운영은 `CORS_ALLOWED_ORIGINS` 로 주입. 와일드카드는 `allowCredentials=true` 와 함께 못 쓴다. **앱(Flutter 네이티브)은 브라우저가 아니라 CORS 대상이 아니다** — 이 설정은 나중의 웹을 위한 것 |
| actuator 는 **`health` 만 노출**, `show-details: never` | 로드밸런서 · 모니터링에 필요한 건 UP/DOWN 뿐. 나머지(`env` 등)는 401 |

### 구현/해결 내용 (`acc27e3`)
```diff
-    public SecurityConfig(JwtTokenProvider tokenProvider) {
+    public SecurityConfig(JwtTokenProvider tokenProvider,
+                          @Value("${springdoc.api-docs.enabled:true}") boolean apiDocsEnabled,
+                          @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
 ...
+                    if (apiDocsEnabled) {
+                        auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
+                    }
 ...
-        config.setAllowedOriginPatterns(List.of("*"));
+        config.setAllowedOrigins(allowedOrigins);
```
- `application-prod.yml`(신규): springdoc 끔 · SQL 로그 끔 · 바인딩 파라미터 로그 끔 · `baseline-on-migrate: false`
- `spring-boot-starter-actuator` 추가

### Before / After
| 요청 | dev 프로필 | prod — 09-02 로컬 검증 | prod — 10-15 재확인(운영 서버) |
|---|---|---|---|
| `/actuator/health` | 200 | 200 (UP) | **200** `{"status":"UP"}` |
| `/v3/api-docs` · `/swagger-ui` | 200 | 401 | **401 · 401** |
| dev 로그인(`provider: dev`) | 200 | 401 | **401** |
| `/actuator/env` | — | 401 | **401** |
| 허용 안 된 Origin 의 CORS 프리플라이트 | — | 403 | **403** |
| 허용 Origin(`localhost:3000`) | — | 통과 | (운영 허용 목록은 미확인) |

10-15 재확인 원문(공개 주소로만 요청):
```text
health: 200 {"groups":["liveness","readiness"],"status":"UP"}
actuator/env: 401
v3/api-docs: 401
swagger-ui: 401
dev login: 401
--- CORS preflight (Origin: https://evil.example)
HTTP/1.1 403 Forbidden
```

### 테스트 🧪
- 09-02: prod 프로필로 로컬 기동해 위 표 확인 + **dev 프로필은 그대로**(dev 로그인 · Swagger 200) 확인
- 10-15: 운영 서버에서 같은 결과 재확인(위)
- 자동 테스트는 없다 — `SecurityConfig` 의 프로필 분기를 검증하는 통합 테스트가 없음

### 남은 과제
- 운영 CORS 허용 목록에 실제로 무엇이 들어가 있는지는 이 문서에서 확인하지 않았다 — **확인 필요**(서버 `.env` 의 `CORS_ALLOWED_ORIGINS`)
- 위 401/403 을 고정하는 보안 통합 테스트 추가

---

## 3. 배포처 선정 — Railway → Render → Oracle → Vultr 🧭

### 배경/문제
조건: **PostgreSQL + Redis + WebSocket 상시 연결(STOMP 프레즌스) + 한국 사용자 + 1인 MVP.** WebSocket 상시 연결이 서버리스를 걸러내고, Redis 가 "DB 만 주는" 호스팅을 걸러낸다. 그리고 포트폴리오 제출일(11-02)까지 실사용 기간을 확보해야 했다.

### 선정 이유 — 네 번 바뀌었다
| 날짜 | 후보 | 판단 | 근거 |
|---|---|---|---|
| 09-02 | **Railway** | 처음 추천 → **철회** | "깃 연결만 하면 PG · Redis 까지". 그런데 "$5 ~ 15/월"은 앱 하나 기준이었다. **PG + Redis 를 붙이면 월 $15 ~ 30** 으로 정정 |
| 09-02 | **Render** | 탈락 | 유료 합계 약 **$23/월**(Redis 만 $10~). 무료 티어는 **15분 유휴 시 잠듦**(프레즌스가 죽음) + **무료 PG 30일 만료** — 이 앱엔 치명적 |
| 09-02 | Firebase | 해당 없음 | Firestore(NoSQL) · Redis 없음 · 상주 프로세스 불가. 올리려면 백엔드를 다시 써야 한다. **FCM 은 배포처와 무관**하게 쓴다 |
| 09-02 | Cloud Run(서울) | 보류 | WebSocket 이 열려 있으면 인스턴스가 계속 과금되고, 무료 티어는 미국 리전만 |
| 09-02 ~ 06 | **Oracle Cloud Always Free** | 선택 → **가입 거절(09-10)** | 무료 + VM 에 PG · Redis 직접 설치. 서울 리전은 신규 무료 가입이 막혀 도쿄로 진행. A1 스펙은 당시 확인 기준 2 OCPU / 12GB(그해 6월에 4 / 24 에서 축소) |
| 09-10 | **Vultr VPS 서울** | **최종** | 아래 |

Oracle 거절 뒤 비교:
| | 비용 | 판단 |
|---|---|---|
| **저가 VPS**(Vultr · Linode) | 월 $6 ~ 12 | ✅ 이미 만든 `docker-compose.prod.yml` 을 그대로 올린다. 10월에 다시 옮길 필요도 없다 |
| Render 유료 | 월 $23 | 비싸고, Docker 로 만든 이전 준비가 무의미 |
| GCP $300 크레딧 | 90일 무료(서울) | 12월 초면 끝 |
| AWS 프리티어 | 조건부 | 당시 정책 변경을 확인하지 못함 |

면접 관점도 있었다 — "VM 에 직접 Docker 로 올리고 방화벽 · HTTPS 를 붙였다"는 "PaaS 버튼을 눌렀다"보다 이야기할 거리가 많다.

### 구현/해결 내용 — Vultr 생성 설정(09-10)
| 항목 | 선택 | 이유 |
|---|---|---|
| 플랜 | Shared CPU `vc2-1c-2gb`(1 vCPU / 2GB / 55GB) | 처음 화면에 Dedicated(월 $60~)가 선택돼 있었다. 1GB 는 Spring + PG + Redis 에 빠듯 |
| 위치 | **Seoul** | 단계를 넘어가며 **Atlanta 로 리셋**된 걸 확인 화면에서 발견해 되돌림(왕복 200ms+ 차이) |
| OS | **Ubuntu 24.04 LTS** | 기본값이 26.04 였다. 막 나온 버전은 Docker 공식 저장소 지원이 늦을 수 있다 |
| 자동 백업 | 끔(월 +$2) | 당시 데이터는 테스트용이고 스택은 git + compose 로 재구축 가능. 실데이터가 생기면 `pg_dump` 로(→ 09-16 DB 일일 백업) |
| SSH | ed25519 키, 비밀번호 대신 | 공개키만 등록. 개인키는 개발 PC 밖으로 나가지 않는다. 키 주석은 이메일 대신 `tuk-server` |
| 방화벽 그룹 | 안 붙임 → 서버에서 `ufw` 로 22 · 80 · 443 만 | DB · Redis 는 어차피 호스트 포트에 안 묶는다(§4) |
| 합계 | **월 $10** | |

### Before / After
| | Before | After |
|---|---|---|
| 배포처 | 미정(무료를 찾는 중) | Vultr 서울 2GB, 월 $10 |
| 이전 비용 | — | Docker 이미지 · compose 라 다른 VM 으로 옮기기 쉬움 |

### 테스트 🧪
해당 없음(선정). 실제 기동은 09-15([FEATURE_NOTES_2026-09-15_09-18 §1](https://github.com/pyosunyoung/tuk-app/blob/main/docs/FEATURE_NOTES_2026-09-15_09-18.md)).

### 남은 과제
- 가격 · 무료 티어 조건은 2026-09 당시 기준이다. 자주 바뀐다
- 처음 추천(Railway)의 비용을 낙관적으로 봤던 건 정정했지만, **추천 전에 PG + Redis 포함 비용을 확인했어야 했다**

---

## 4. Dockerfile + 운영 compose 🆕 🧭

### 배경/문제
배포처가 정해지기 전이었다(09-06). 어디로 가든 **같은 이미지**를 쓰면 호스팅을 옮기는 비용이 거의 없다.

### 선정 이유
| 결정 | 이유 |
|---|---|
| **`TZ=Asia/Seoul` 고정**(Dockerfile · compose 양쪽) | 번개 만료가 서버 벽시계(`LocalDateTime`) 기준이라 **UTC 컨테이너면 만료 시각이 9시간 어긋난다.** 첫 배포에서 바로 터지는 종류 |
| 멀티스테이지(JDK 빌드 → JRE 실행), 의존성 레이어 먼저 | 이미지 크기 · 빌드 캐시 |
| **비루트 사용자**로 실행 | 컨테이너가 뚫려도 root 가 아니게 |
| DB · Redis 는 **`expose` 만**(호스트 포트 없음) | 공개 IP 에 5432 가 열려 있으면 바로 스캔당한다. 앱만 내부 네트워크로 붙는다 |
| Redis **AOF 켬** | refresh 토큰이 Redis 에 있다. 끄면 재시작할 때마다 전원 재로그인 |
| FCM 서비스 계정 키는 **이미지에 굽지 않고 읽기 전용 볼륨** | `.dockerignore` 로도 차단. 이미지가 유출돼도 키는 없다 |
| `.env.example` 만 커밋 | 실제 값은 서버에서 만든다(DB 비밀번호 · JWT 시크릿은 로컬에 없음) |

### 구현/해결 내용 (`c2b4f03`)
`Dockerfile` · `docker-compose.prod.yml`(app · postgres · redis) · `.dockerignore` · `.env.example`. 앱 헬스체크는 §2 의 `/actuator/health`.

### Before / After
| | Before | After |
|---|---|---|
| 실행 | 개발 PC 의 `./gradlew bootRun` + 로컬 PG · Redis | `docker compose -f docker-compose.prod.yml up -d` 한 줄 |
| 시간대 | 실행 환경 따라 다름 | 컨테이너 안 KST 고정 |

### 테스트 🧪
**개발 PC 에 Docker 가 없어 이미지 빌드를 검증하지 못했다**(커밋 메시지에 명시). `gradlew` 가 LF 인 것만 확인했다(CRLF 면 컨테이너 빌드 실패). 첫 검증은 09-15 배포 서버에서 했고, 거기서 **compose 파일 YAML 파싱 오류**가 나왔다 — §5 에서 추가한 `TUK_DOMAIN` 안내 문구의 `: ` 가 원인([FEATURE_NOTES_2026-09-15_09-18 §2](https://github.com/pyosunyoung/tuk-app/blob/main/docs/FEATURE_NOTES_2026-09-15_09-18.md)).

### 남은 과제
- 로컬에서 검증할 수 없는 파일을 커밋한 셈이었다. 이후 CI 가 이미지를 빌드하면서 해결(09-16~)
- 운영 compose 의 PostgreSQL 이미지는 `postgres:16`, 개발 PC 는 PostgreSQL 17 이다. 덤프 · 복원 호환에 영향이 있는지 **확인 필요**

---

## 5. Caddy — HTTPS 자동 발급 🆕 🧭

### 배경/문제
**안드로이드는 평문 HTTP 통신을 기본으로 막는다**(Android 9+). 배포 앱이 서버와 통신하려면 HTTPS 가 필수다.

### 선정 이유
| 결정 | 고려한 것 | 고른 이유 |
|---|---|---|
| **Caddy** | nginx + certbot + 갱신 크론 | 도메인만 적으면 Let's Encrypt 인증서를 **자동 발급 · 갱신**한다. WebSocket 업그레이드도 설정 없이 통과 |
| 80 · 443 만 Caddy 가 연다 | 앱 8080 공개 | 앱 · DB · Redis 는 외부 포트 0개 |
| 인증서는 `caddy-data` 볼륨 | 컨테이너 안 | 지우면 재발급 — Let's Encrypt 발급 한도에 걸릴 수 있다 |

### 구현/해결 내용 (`a263e42`)
```caddy
{$TUK_DOMAIN} {
	encode gzip
	reverse_proxy app:8080 {
		header_up X-Real-IP {remote_host}
	}
}
```
```diff
   app:
-    ports:
-      - "8080:8080"
+    expose:
+      - "8080"
+  caddy:
+    ports:
+      - "80:80"     # Let's Encrypt 인증(HTTP-01)에 필요. HTTPS 로 자동 리다이렉트
+      - "443:443"
```

### Before / After
| | Before(`c2b4f03`) | After(`a263e42`) |
|---|---|---|
| 외부 공개 | 앱 8080 직접 | Caddy 80/443 만 |
| 암호화 | 없음 | HTTPS(자동 발급 · 갱신) |

### 테스트 🧪
이 기간엔 실행 검증 없음(서버가 아직 없었다). 09-15 배포에서 확인 — HTTP → HTTPS 308, Let's Encrypt 인증서 자동 발급, 8080 외부 차단, `/ws` 101([FEATURE_NOTES_2026-09-15_09-18 §1](https://github.com/pyosunyoung/tuk-app/blob/main/docs/FEATURE_NOTES_2026-09-15_09-18.md)).

### 남은 과제
- 도메인: 당시 진짜 도메인 대신 무료 동적 DNS 를 제안했고, 실제로는 sslip.io 임시 도메인으로 갔다(09-15). 진짜 도메인은 10-15 기준 아직 없음

---

## 6. 2GB VM 에 맞춘 메모리 상한 ⚙️

### 배경/문제
Dockerfile 이 JVM 에 `-XX:MaxRAMPercentage=75` 를 준다. 컨테이너에 상한이 없으면 JVM 은 **호스트 전체(2GB)의 75% = 약 1.5GB** 까지 힙을 키울 수 있고, 같은 VM 의 PostgreSQL · Redis 와 부딪혀 OOM 이 난다.

### 선정 이유
2GB 를 컨테이너별로 나누고 OS 몫을 남긴다. 더 큰 VM(4GB, 월 $24)은 당시 트래픽에 과했다 — Vultr 는 재생성 없이 리사이즈할 수 있어 모자라면 그때 올린다.

### 구현/해결 내용 (`12e2b1c`)
| 컨테이너 | 상한 | 비고 |
|---|---|---|
| app | 1g | JVM 은 이 안에서 75% |
| postgres | 384m | |
| redis | 192m | `maxmemory 128mb` + `volatile-lru` — 프레즌스는 TTL 로 사라지고 refresh 토큰은 작다 |
| caddy | 96m | |
| 합계 | 약 1.7GB | 나머지는 OS |

### Before / After
| | Before | After |
|---|---|---|
| JVM 힙 상한(75%) | 호스트 2GB 기준 → 약 1.5GB | 컨테이너 1GB 기준 → 약 768MB |
| 다른 컨테이너 | 상한 없음 | 합계 약 1.7GB 안에서 |

### 테스트 🧪
이 기간엔 실행 검증 없음. 09-15 배포 직후 **2GB 중 1.1GB 여유**([FEATURE_NOTES_2026-09-15_09-18 §1](https://github.com/pyosunyoung/tuk-app/blob/main/docs/FEATURE_NOTES_2026-09-15_09-18.md) 수치 표).

### 남은 과제
- 당시 "스왑 2GB 도 잡겠다"고 제안했는데, 실제로 설정했는지 기록이 없다 — **확인 필요**(서버에서 `swapon --show`)

---

## 7. 검증 현황 🧪

| 항목 | 이 기간(09-02 ~ 09-10) | 이후 |
|---|---|---|
| Flyway | 기존 DB · 빈 DB 모두 로컬에서 기동 검증 | V2 ~ V5 가 같은 경로로 적용(10-15 로컬 이력 확인) |
| 운영 프로필 | prod 프로필 로컬 기동 — 200 / 401 / 403 확인 | 10-15 운영 서버에서 같은 결과 재확인 |
| Docker · Caddy · 메모리 | **실행 검증 불가**(개발 PC 에 Docker 없음, 서버 없음) | 09-15 첫 배포에서 YAML 오류 1건 발견 · 수정 후 가동 |
| 자동 테스트 | 82개 그대로(설정 변경이라 늘지 않음) | 09-16 부터 CI 에서 매 push 실행 |

---

## 경력기술서용 요약

형식: **문제 → 한 행동 → 결과(수치)**.

- Hibernate `ddl-auto: update` 가 운영 스키마를 임의로 바꾸고 이력도 없던 상태 → 실제 스키마를 `pg_dump` 로 떠 Flyway V1 baseline 을 만들고 `validate` 로 전환, 기존 DB 는 baseline 으로 건너뛰게 구성 → **기존 데이터 보존 + 빈 DB 에서 V1 적용 모두 검증**, 이후 스키마 변경 4건(V2 ~ V5)이 전부 SQL 파일로 이력에 남음.
- Spring Boot 4 에서 `flyway-core` 만 넣으면 마이그레이션이 로그 없이 실행되지 않음 → 자동 구성이 모듈로 분리된 것을 확인하고 `spring-boot-flyway` 추가 → 프로젝트 문서에 Boot 4 함정으로 기록.
- 공개 직전 서버에 Swagger 전체 공개 · 와일드카드 CORS · 죽은 헬스체크 설정 → 운영 프로필로 분리하고 Swagger 경로의 인증 예외 등록 자체를 설정값으로 끄도록 변경 → **운영에서 API 문서 · dev 로그인 · actuator 상세 401, 외부 출처 CORS 403, health 200** (배포 한 달 뒤 재확인에서도 동일), 개발 프로필은 영향 없음.
- PostgreSQL + Redis + WebSocket 상시 연결이 필요한데 무료 호스팅이 맞지 않음(15분 유휴 잠듦 · 무료 DB 30일 만료 · 가입 거절) → Railway · Render · Cloud Run · Oracle · GCP 크레딧 · VPS 를 조건별로 비교하고 Docker 이미지 기반 VPS 로 결정 → **서울 리전 월 $10**(당시 Render 유료 추정 월 $23 의 절반 이하).
- 2GB VM 하나에 앱 · DB · Redis · 프록시를 올리면 JVM 이 호스트 메모리의 75% 를 잡아 OOM 위험 → 컨테이너별 상한(합계 약 1.7GB) + Redis `maxmemory` 지정 → 배포 후 **1.1GB 여유**로 가동.

---

## 이번 작업으로 수정·생성한 파일

| 리포 | 파일 | 무엇 |
|---|---|---|
| tuk-server | `docs/OPS_NOTES_2026-09.md` | 이 문서(비어 있던 파일에 작성) |

코드 · 설정 변경은 각 항목의 커밋 해시 참고. 이 기간은 화면이 없는 작업이라 캡처는 터미널 출력으로 대신했고, 이미지 TODO 는 없다.

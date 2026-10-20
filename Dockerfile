# 툭 백엔드 이미지 (멀티스테이지)
#
# 빌드:  docker build -t tuk-server .
# 실행:  docker compose -f docker-compose.prod.yml up -d
#
# ⚠️ TZ=Asia/Seoul 필수 — 번개 만료(expires_at)가 서버의 LocalDateTime(벽시계) 기준이라
#    UTC 컨테이너에서 돌리면 만료 시각이 9시간 어긋난다.

# --- 1) 빌드 단계 -----------------------------------------------------------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 의존성만 먼저 받아 레이어로 캐싱한다(소스만 바뀌면 이 단계를 재사용).
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src
# 테스트는 CI/로컬에서 이미 돌린다. 이미지 빌드는 패키징만.
RUN ./gradlew bootJar --no-daemon -x test

# --- 2) 실행 단계 -----------------------------------------------------------
FROM eclipse-temurin:17-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends tzdata curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd -r -u 1001 -m tuk

ENV TZ=Asia/Seoul \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
# 로그 폴더는 이미지에서 미리 만들어 tuk 소유로 둔다 — 이름 있는 볼륨은 처음 붙을 때 이 소유권을 그대로 물려받는다
RUN mkdir -p /app/logs && chown -R tuk:tuk /app
USER tuk

EXPOSE 8080

# 컨테이너 오케스트레이터가 살아있는지 확인하는 창구(actuator health 만 열어둠)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -fs http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

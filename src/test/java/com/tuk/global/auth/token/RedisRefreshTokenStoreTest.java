package com.tuk.global.auth.token;

import com.tuk.global.auth.token.RefreshTokenStore.RotationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * rotation 의 원자성은 Lua 스크립트가 Redis 안에서 끊기지 않고 실행된다는 데 기대고 있다.
 * 인메모리 store 로는 그걸 검증할 수 없어서 실제 Redis 에 같은 토큰으로 동시에 요청한다.
 */
@SpringBootTest
class RedisRefreshTokenStoreTest {

    private static final Long USER_ID = -9_001L; // 실제 사용자와 겹치지 않게
    private static final Duration TTL = Duration.ofMinutes(5);

    @Autowired RedisRefreshTokenStore store;

    @AfterEach
    void cleanUp() {
        store.delete(USER_ID);
    }

    @Test
    @DisplayName("최신 토큰이면 교체, 교체된 옛 토큰이 다시 오면 재사용으로 보고 세션을 파기한다")
    void rotateThenReuse() {
        store.save(USER_ID, "old", TTL);

        assertThat(store.rotate(USER_ID, "old", "new", TTL)).isEqualTo(RotationResult.ROTATED);
        assertThat(store.find(USER_ID)).contains("new");

        assertThat(store.rotate(USER_ID, "old", "other", TTL)).isEqualTo(RotationResult.REUSE_DETECTED);
        assertThat(store.find(USER_ID)).isEmpty();

        assertThat(store.rotate(USER_ID, "new", "x", TTL)).isEqualTo(RotationResult.NOT_FOUND);
    }

    @RepeatedTest(20)
    @DisplayName("같은 토큰으로 동시에 2번 와도 교체는 하나만 — 나머지는 재사용으로 판정")
    void concurrentRotationRotatesOnlyOnce() throws Exception {
        store.save(USER_ID, "old", TTL);

        List<RotationResult> results = race(2);

        assertThat(results).containsExactlyInAnyOrder(RotationResult.ROTATED, RotationResult.REUSE_DETECTED);
        // 앱이 refresh 를 하나로 합치지 않으면 이렇게 정상 사용자의 세션도 사라진다(→ 앱 single-flight)
        assertThat(store.find(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("동시에 8번 와도 교체 성공은 정확히 한 번")
    void manyConcurrentRotationsRotateOnlyOnce() throws Exception {
        store.save(USER_ID, "old", TTL);

        List<RotationResult> results = race(8);

        assertThat(results).filteredOn(r -> r == RotationResult.ROTATED).hasSize(1);
        assertThat(results).filteredOn(r -> r == RotationResult.REUSE_DETECTED).hasSize(1);
        assertThat(results).filteredOn(r -> r == RotationResult.NOT_FOUND).hasSize(6); // 파기된 뒤에 도착
        assertThat(store.find(USER_ID)).isEmpty();
    }

    /** n 개 스레드가 출발선에서 기다렸다가 같은 옛 토큰으로 동시에 rotate 한다. */
    private List<RotationResult> race(int n) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<RotationResult>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String newToken = "new-" + i;
                Callable<RotationResult> task = () -> {
                    ready.countDown();
                    start.await();
                    return store.rotate(USER_ID, "old", newToken, TTL);
                };
                futures.add(pool.submit(task));
            }
            ready.await();
            start.countDown();

            List<RotationResult> results = new ArrayList<>();
            for (Future<RotationResult> f : futures) {
                results.add(f.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}

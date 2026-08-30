package com.tuk.presence.ws;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketSessionRegistryTest {

    private final WebSocketSessionRegistry registry = new WebSocketSessionRegistry();

    @Test
    @DisplayName("register/isOnline: 등록하면 온라인")
    void registerOnline() {
        registry.register(1L, "s1");

        assertThat(registry.isOnline(1L)).isTrue();
        assertThat(registry.isOnline(2L)).isFalse();
        assertThat(registry.onlineUsers()).containsExactly(1L);
    }

    @Test
    @DisplayName("멀티기기: 세션 하나 끊겨도 다른 세션이 있으면 온라인 유지, 마지막 세션 끊기면 오프라인")
    void multiSession() {
        registry.register(1L, "s1");
        registry.register(1L, "s2");

        registry.unregister("s1");
        assertThat(registry.isOnline(1L)).isTrue();

        registry.unregister("s2");
        assertThat(registry.isOnline(1L)).isFalse();
        assertThat(registry.onlineUsers()).isEmpty();
    }

    @Test
    @DisplayName("unregister: 모르는 세션은 무시(예외 없음)")
    void unregisterUnknown() {
        registry.unregister("nope");
        assertThat(registry.isOnline(1L)).isFalse();
    }
}

package com.bungae.flash.repository;

import com.bungae.flash.domain.Flash;
import com.bungae.flash.domain.FlashRecipient;
import com.bungae.global.common.Activity;
import com.bungae.global.config.JpaAuditingConfig;
import com.bungae.user.domain.User;
import com.bungae.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class FlashRecipientRepositoryTest {

    @Autowired FlashRepository flashRepository;
    @Autowired FlashRecipientRepository flashRecipientRepository;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager em;

    User sender, me, other;
    LocalDateTime now;

    @BeforeEach
    void setUp() {
        sender = save("sender");
        me = save("me");
        other = save("other");
        now = LocalDateTime.now();
    }

    @Test
    @DisplayName("findActiveReceived: ACTIVE + 만료 전 번개만, PENDING/JOINED 둘 다 포함")
    void findActiveReceived() {
        // 포함되어야 하는 것들
        recipient(flash("active-pending", now.plusHours(1), false), me, false); // PENDING
        recipient(flash("active-joined", now.plusHours(1), false), me, true);   // JOINED(무시 아님)

        // 제외되어야 하는 것들
        recipient(flash("expired-by-time", now.minusHours(1), false), me, false); // 시간 만료
        recipient(flash("expired-by-status", now.plusHours(1), true), me, false); // 상태 EXPIRED
        recipient(flash("for-other", now.plusHours(1), false), other, false);     // 수신자 다름

        em.flush();
        em.clear();

        List<FlashRecipient> received = flashRecipientRepository.findActiveReceived(me.getId(), now);

        assertThat(received)
                .extracting(fr -> fr.getFlash().getMessage())
                .containsExactlyInAnyOrder("active-pending", "active-joined");
    }

    @Test
    @DisplayName("findByFlashIdAndUserId: (flash, user) 로 단건 조회 — 멱등 합류의 기반")
    void findByFlashIdAndUserId() {
        Flash flash = flash("hi", now.plusHours(1), false);
        recipient(flash, me, false);
        em.flush();
        em.clear();

        assertThat(flashRecipientRepository.findByFlashIdAndUserId(flash.getId(), me.getId())).isPresent();
        assertThat(flashRecipientRepository.findByFlashIdAndUserId(flash.getId(), other.getId())).isEmpty();
    }

    private User save(String nickname) {
        return userRepository.save(User.builder()
                .nickname(nickname)
                .provider("kakao")
                .providerId(nickname + "-pid")
                .build());
    }

    private Flash flash(String message, LocalDateTime expiresAt, boolean expired) {
        Flash f = Flash.builder()
                .sender(sender)
                .activity(Activity.MEAL)
                .message(message)
                .expiresAt(expiresAt)
                .build();
        if (expired) f.expire();
        return flashRepository.save(f);
    }

    private void recipient(Flash flash, User user, boolean joined) {
        FlashRecipient fr = FlashRecipient.builder().flash(flash).user(user).build();
        if (joined) fr.join();
        flashRecipientRepository.save(fr);
    }
}

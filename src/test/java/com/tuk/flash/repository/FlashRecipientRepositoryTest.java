package com.tuk.flash.repository;

import com.tuk.flash.domain.Flash;
import com.tuk.flash.domain.FlashRecipient;
import com.tuk.flash.domain.MeetupRoom;
import com.tuk.global.common.Activity;
import com.tuk.global.config.JpaAuditingConfig;
import com.tuk.user.domain.User;
import com.tuk.user.repository.UserRepository;
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

    @Autowired
    MeetupRoomRepository meetupRoomRepository;

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
    @DisplayName("findActiveReceived: 합류 마감이 지나도 약속 시간이 남았으면 계속 보인다")
    void keepsRoomsWithFutureMeetAt() {
        // 마감도 지났고 만료 처리도 됐지만, 약속은 아직 남은 모임 — 어디서 몇 시인지 다시 봐야 한다
        Flash past = flash("meeting-tonight", now.minusHours(1), true);
        room(past, now.plusHours(2));
        recipient(past, me, true);

        // 약속을 안 정한 채 마감이 지난 것은 그대로 사라진다
        Flash forgotten = flash("no-meet-at", now.minusHours(1), true);
        room(forgotten, null);
        recipient(forgotten, me, true);

        em.flush();
        em.clear();

        assertThat(flashRecipientRepository.findActiveReceived(me.getId(), now))
                .extracting(fr -> fr.getFlash().getMessage())
                .containsExactly("meeting-tonight");
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

    private void room(Flash flash, LocalDateTime meetAt) {
        MeetupRoom r = MeetupRoom.builder().flash(flash).expiresAt(flash.getExpiresAt()).build();
        if (meetAt != null) r.setMeetAt(meetAt, sender);
        meetupRoomRepository.save(r);
    }

    private void recipient(Flash flash, User user, boolean joined) {
        FlashRecipient fr = FlashRecipient.builder().flash(flash).user(user).build();
        if (joined) fr.join();
        flashRecipientRepository.save(fr);
    }
}

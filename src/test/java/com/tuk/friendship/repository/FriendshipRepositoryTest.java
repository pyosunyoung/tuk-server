package com.tuk.friendship.repository;

import com.tuk.friendship.domain.Friendship;
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

import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class FriendshipRepositoryTest {

    @Autowired FriendshipRepository friendshipRepository;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager em;

    User a, b, c, d;

    @BeforeEach
    void setUp() {
        a = save("a");
        b = save("b");
        c = save("c");
        d = save("d");

        // a-b: 수락 (a 가 신청자)
        accepted(a, b);
        // c-a: 수락 (a 가 수신자) → 방향 무관 검증용
        accepted(c, a);
        // a-d: 대기 (a 가 신청자)
        friendshipRepository.save(Friendship.builder().requester(a).addressee(d).build());

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("findAcceptedFriendIds: 방향 무관하게 ACCEPTED 절친 ID 만, 자기 자신·PENDING 은 제외")
    void findAcceptedFriendIds() {
        List<Long> friendIds = friendshipRepository.findAcceptedFriendIds(a.getId());

        assertThat(friendIds)
                .containsExactlyInAnyOrder(b.getId(), c.getId())
                .doesNotContain(a.getId(), d.getId());
    }

    @Test
    @DisplayName("findBetween: 방향에 상관없이 두 사람 사이 관계를 찾는다")
    void findBetween() {
        // c-a 는 (c 신청, a 수신)으로 저장됐지만 반대 방향으로 조회해도 찾아야 함
        assertThat(friendshipRepository.findBetween(a.getId(), c.getId())).isPresent();
        // 관계없는 b-d 는 없음
        assertThat(friendshipRepository.findBetween(b.getId(), d.getId())).isEmpty();
    }

    @Test
    @DisplayName("uq_friend_pair: 방향만 다른 중복 쌍은 DB 가 거부한다(경합 방지)")
    void reversePairRejected() {
        // setUp 에서 a→b 가 이미 저장됨. 반대 방향 b→a 는 정규화 쌍(user_low_id,user_high_id)이 같아 유니크 위반.
        Friendship reverse = Friendship.builder().requester(b).addressee(a).build();

        assertThatThrownBy(() -> friendshipRepository.saveAndFlush(reverse))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findPendingReceived: 내가 받은 수락 대기 신청만, 신청자까지 로딩")
    void findPendingReceived() {
        // d 는 a 의 신청을 받은 상태(PENDING)
        List<Friendship> received = friendshipRepository.findPendingReceived(d.getId());
        assertThat(received).hasSize(1);
        assertThat(received.get(0).getRequester().getId()).isEqualTo(a.getId());

        // a 가 받은 PENDING 신청은 없음 (c-a 는 이미 ACCEPTED)
        assertThat(friendshipRepository.findPendingReceived(a.getId())).isEmpty();
    }

    private User save(String nickname) {
        return userRepository.save(User.builder()
                .nickname(nickname)
                .provider("kakao")
                .providerId(nickname + "-pid")
                .build());
    }

    private void accepted(User requester, User addressee) {
        Friendship f = Friendship.builder().requester(requester).addressee(addressee).build();
        f.accept();
        friendshipRepository.save(f);
    }
}

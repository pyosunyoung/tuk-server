package com.tuk.user.repository;

import com.tuk.global.config.JpaAuditingConfig;
import com.tuk.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V6 의 초대 코드 유일 인덱스를 실제 PostgreSQL 에서 확인한다.
 * 서비스는 생성할 때 "이미 있는지"를 먼저 보지만, 확인과 저장 사이 경합의 마지막 방어선은 DB 다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class UserInviteCodeConstraintTest {

    @Autowired UserRepository userRepository;

    @Test
    @DisplayName("같은 초대 코드를 두 사용자에게 저장하면 DB 가 거부한다")
    void rejectsDuplicateInviteCode() {
        User a = user("a");
        a.assignInviteCode("DUPL2345");
        userRepository.saveAndFlush(a);

        User b = user("b");
        b.assignInviteCode("DUPL2345");

        assertThatThrownBy(() -> userRepository.saveAndFlush(b))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("코드를 아직 안 만든(NULL) 사용자는 여러 명이어도 된다")
    void allowsManyUsersWithoutCode() {
        userRepository.saveAndFlush(user("x"));

        assertThatCode(() -> userRepository.saveAndFlush(user("y"))).doesNotThrowAnyException();
    }

    private User user(String name) {
        return User.builder()
                .nickname(name)
                .provider("dev")
                .providerId("invite-uq-" + name + "-" + System.nanoTime())
                .build();
    }
}

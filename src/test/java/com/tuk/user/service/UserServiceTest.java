package com.tuk.user.service;

import com.tuk.global.exception.NotFoundException;
import com.tuk.user.domain.User;
import com.tuk.user.dto.MeResponse;
import com.tuk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks UserService userService;

    @Test
    @DisplayName("getMe: 내 id·닉네임·프로필을 그대로 돌려준다")
    void getMe() {
        User me = User.builder()
                .provider("kakao")
                .providerId("5004466861")
                .nickname("표선영")
                .profileImage("https://img.kakao/p.jpg")
                .build();
        ReflectionTestUtils.setField(me, "id", 339L);
        given(userRepository.findById(339L)).willReturn(Optional.of(me));

        MeResponse res = userService.getMe(339L);

        assertThat(res.userId()).isEqualTo(339L);
        assertThat(res.nickname()).isEqualTo("표선영");
        assertThat(res.profileImage()).isEqualTo("https://img.kakao/p.jpg");
    }

    @Test
    @DisplayName("getMe: 토큰은 유효한데 유저가 없으면 404 (탈퇴·DB 초기화)")
    void getMeNotFound() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMe(999L))
                .isInstanceOf(NotFoundException.class);
    }
}

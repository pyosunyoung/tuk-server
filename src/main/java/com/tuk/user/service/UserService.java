package com.tuk.user.service;

import com.tuk.global.exception.NotFoundException;
import com.tuk.user.domain.User;
import com.tuk.user.dto.MeResponse;
import com.tuk.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 내 정보 조회.
     * JWT 는 유효한데 유저가 없는 경우(탈퇴·DB 초기화)는 404 로 알린다 —
     * 앱이 세션을 정리하고 다시 로그인시키게.
     */
    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
        return MeResponse.from(user);
    }
}

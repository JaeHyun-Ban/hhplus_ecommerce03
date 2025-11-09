package com.hhplus.ecommerce.application.user;

import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRole;
import com.hhplus.ecommerce.domain.user.UserStatus;
import com.hhplus.ecommerce.infrastructure.persistence.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 사용자 관리 서비스
 *
 * UseCase:
 * - UC-002: 사용자 등록
 * - UC-003: 사용자 조회
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    /**
     * UC-002: 사용자 등록
     *
     * @param email 이메일
     * @param password 비밀번호 (암호화되어야 함)
     * @param name 이름
     * @return 생성된 사용자
     */
    @Transactional
    public User registerUser(String email, String password, String name) {
        // 입력값 검증
        validateEmail(email);
        validatePassword(password);
        validateName(name);

        // 이메일 중복 확인
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다: " + email);
        }

        // 사용자 생성
        User user = User.builder()
                .email(email)
                .password(password) // TODO: 실제로는 암호화되어야 함
                .name(name)
                .balance(BigDecimal.ZERO)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        return userRepository.save(user);
    }

    /**
     * UC-003: 사용자 조회
     *
     * @param userId 사용자 ID
     * @return 사용자 정보
     */
    public User getUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 탈퇴한 사용자는 조회 불가
        if (user.getStatus() == UserStatus.DELETED) {
            throw new IllegalArgumentException("탈퇴한 사용자입니다: " + userId);
        }

        return user;
    }

    // ========================================
    // Private 검증 메서드
    // ========================================

    private void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("이메일은 필수입니다");
        }

        // 간단한 이메일 형식 검증
        if (!email.contains("@") || !email.contains(".")) {
            throw new IllegalArgumentException("올바른 이메일 형식을 입력해주세요");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("비밀번호는 최소 8자 이상이어야 합니다");
        }
    }

    private void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("이름은 필수입니다");
        }

        if (name.length() < 2 || name.length() > 50) {
            throw new IllegalArgumentException("이름은 2~50자 사이여야 합니다");
        }
    }
}
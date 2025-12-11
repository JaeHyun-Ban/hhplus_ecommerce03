package com.hhplus.ecommerce.user.application;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.user.domain.User;
import com.hhplus.ecommerce.user.domain.UserRole;
import com.hhplus.ecommerce.user.domain.UserStatus;
import com.hhplus.ecommerce.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * UserService 통합 테스트 (TestContainers 사용)
 *
 * 테스트 전략:
 * - 실제 MySQL 컨테이너를 사용한 통합 테스트
 * - 사용자 등록, 조회 등 실제 DB 기반 테스트
 */
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("UserService 통합 테스트 (TestContainers)")
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 초기화
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("UC-002: 사용자 등록 테스트")
    class RegisterUserTest {

        @Test
        @DisplayName("성공: 사용자 등록")
        void registerUser_Success() {
            // Given
            String email = "test@example.com";
            String password = "password123";
            String name = "홍길동";

            // When
            User result = userService.registerUser(email, password, name);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getEmail()).isEqualTo(email);
            assertThat(result.getName()).isEqualTo(name);
            assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getRole()).isEqualTo(UserRole.USER);
            assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);

            // DB에서 확인
            User savedUser = userRepository.findById(result.getId()).orElseThrow();
            assertThat(savedUser.getEmail()).isEqualTo(email);
            assertThat(savedUser.getPassword()).isEqualTo(password);
            assertThat(savedUser.getName()).isEqualTo(name);
        }

        @Test
        @DisplayName("실패: 이메일 null")
        void registerUser_EmailNull() {
            // Given
            String email = null;
            String password = "password123";
            String name = "홍길동";

            // When & Then
            assertThatThrownBy(() -> userService.registerUser(email, password, name))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("이메일은 필수입니다");
        }

        @Test
        @DisplayName("실패: 이메일 빈 문자열")
        void registerUser_EmailEmpty() {
            // Given
            String email = "   ";
            String password = "password123";
            String name = "홍길동";

            // When & Then
            assertThatThrownBy(() -> userService.registerUser(email, password, name))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("이메일은 필수입니다");
        }

        @Test
        @DisplayName("실패: 이메일 형식 오류 (@ 없음)")
        void registerUser_EmailInvalidFormat_NoAtSymbol() {
            // Given
            String email = "testexample.com";
            String password = "password123";
            String name = "홍길동";

            // When & Then
            assertThatThrownBy(() -> userService.registerUser(email, password, name))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("올바른 이메일 형식을 입력해주세요");
        }

        @Test
        @DisplayName("실패: 이메일 형식 오류 (. 없음)")
        void registerUser_EmailInvalidFormat_NoDot() {
            // Given
            String email = "test@examplecom";
            String password = "password123";
            String name = "홍길동";

            // When & Then
            assertThatThrownBy(() -> userService.registerUser(email, password, name))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("올바른 이메일 형식을 입력해주세요");
        }

        @Test
        @DisplayName("실패: 비밀번호 null")
        void registerUser_PasswordNull() {
            // Given
            String email = "test@example.com";
            String password = null;
            String name = "홍길동";

            // When & Then
            assertThatThrownBy(() -> userService.registerUser(email, password, name))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("비밀번호는 최소 8자 이상이어야 합니다");
        }

        @Test
        @DisplayName("실패: 비밀번호 8자 미만")
        void registerUser_PasswordTooShort() {
            // Given
            String email = "test@example.com";
            String password = "pass123"; // 7자
            String name = "홍길동";

            // When & Then
            assertThatThrownBy(() -> userService.registerUser(email, password, name))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("비밀번호는 최소 8자 이상이어야 합니다");
        }

        @Test
        @DisplayName("실패: 이름 null")
        void registerUser_NameNull() {
            // Given
            String email = "test@example.com";
            String password = "password123";
            String name = null;

            // When & Then
            assertThatThrownBy(() -> userService.registerUser(email, password, name))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("이름은 필수입니다");
        }

        @Test
        @DisplayName("실패: 이름 빈 문자열")
        void registerUser_NameEmpty() {
            // Given
            String email = "test@example.com";
            String password = "password123";
            String name = "   ";

            // When & Then
            assertThatThrownBy(() -> userService.registerUser(email, password, name))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("이름은 필수입니다");
        }

        @Test
        @DisplayName("실패: 이름 2자 미만")
        void registerUser_NameTooShort() {
            // Given
            String email = "test@example.com";
            String password = "password123";
            String name = "홍"; // 1자

            // When & Then
            assertThatThrownBy(() -> userService.registerUser(email, password, name))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("이름은 2~50자 사이여야 합니다");
        }

        @Test
        @DisplayName("실패: 이름 50자 초과")
        void registerUser_NameTooLong() {
            // Given
            String email = "test@example.com";
            String password = "password123";
            String name = "a".repeat(51); // 51자

            // When & Then
            assertThatThrownBy(() -> userService.registerUser(email, password, name))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("이름은 2~50자 사이여야 합니다");
        }

        @Test
        @DisplayName("실패: 이메일 중복")
        void registerUser_EmailAlreadyExists() {
            // Given
            String email = "test@example.com";
            String password = "password123";
            String name = "홍길동";

            // 먼저 사용자 등록
            userService.registerUser(email, password, name);

            // When & Then - 같은 이메일로 다시 등록 시도
            assertThatThrownBy(() -> userService.registerUser(email, "newPassword", "김철수"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("이미 사용 중인 이메일입니다");
        }
    }

    @Nested
    @DisplayName("UC-003: 사용자 조회 테스트")
    class GetUserTest {

        @Test
        @DisplayName("성공: 사용자 조회")
        void getUser_Success() {
            // Given
            String email = "test@example.com";
            String password = "password123";
            String name = "홍길동";

            // 사용자 등록
            User registered = userService.registerUser(email, password, name);

            // When
            User result = userService.getUser(registered.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(registered.getId());
            assertThat(result.getEmail()).isEqualTo(email);
            assertThat(result.getName()).isEqualTo(name);
            assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("성공: INACTIVE 상태 사용자도 조회 가능")
        void getUser_InactiveUser_Success() {
            // Given
            User user = User.builder()
                    .email("inactive@example.com")
                    .password("password123")
                    .name("비활성사용자")
                    .balance(BigDecimal.valueOf(10000))
                    .role(UserRole.USER)
                    .status(UserStatus.INACTIVE)
                    .build();

            User saved = userRepository.save(user);

            // When
            User result = userService.getUser(saved.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(UserStatus.INACTIVE);
        }

        @Test
        @DisplayName("실패: 사용자를 찾을 수 없음")
        void getUser_UserNotFound() {
            // Given
            Long nonExistentId = 999L;

            // When & Then
            assertThatThrownBy(() -> userService.getUser(nonExistentId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("사용자를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("실패: 탈퇴한 사용자 (DELETED 상태)")
        void getUser_DeletedUser() {
            // Given
            User user = User.builder()
                    .email("deleted@example.com")
                    .password("password123")
                    .name("탈퇴한사용자")
                    .balance(BigDecimal.ZERO)
                    .role(UserRole.USER)
                    .status(UserStatus.DELETED)
                    .build();

            User saved = userRepository.save(user);

            // When & Then
            assertThatThrownBy(() -> userService.getUser(saved.getId()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("탈퇴한 사용자입니다");
        }
    }
}

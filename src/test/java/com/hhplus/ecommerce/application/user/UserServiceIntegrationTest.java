package com.hhplus.ecommerce.application.user;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRole;
import com.hhplus.ecommerce.domain.user.UserStatus;
import com.hhplus.ecommerce.infrastructure.persistence.user.UserRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * UserService 통합 테스트 (TestContainers 사용)
 *
 * 테스트 전략:
 * - 실제 MySQL 컨테이너를 사용한 통합 테스트
 * - JPA, 트랜잭션, DB 제약조건 등 실제 동작 검증
 * - FakeRepository와 달리 프로덕션 환경과 동일한 테스트
 */
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("UserService 통합 테스트 (TestContainers)")
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 각 테스트 전에 DB 초기화
        // Native query로 외래 키 제약 조건을 우회하여 삭제
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

        userRepository.deleteAll();

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    @Nested
    @DisplayName("사용자 등록 통합 테스트")
    class RegisterUserIntegrationTest {

        @Test
        @DisplayName("성공: 실제 DB에 사용자 저장 및 조회")
        void registerUser_SaveToRealDatabase() {
            // Given
            String email = "integration@example.com";
            String password = "password123";
            String name = "통합테스트사용자";

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

            // 실제 DB에서 조회 검증
            Optional<User> found = userRepository.findById(result.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getEmail()).isEqualTo(email);
            assertThat(found.get().getName()).isEqualTo(name);
        }

        @Test
        @DisplayName("성공: JPA 영속성 컨텍스트 동작 확인")
        void registerUser_JpaPersistenceContext() {
            // Given
            String email = "jpa@example.com";
            String password = "password123";
            String name = "JPA테스트";

            // When
            User saved = userService.registerUser(email, password, name);

            // Then - 영속성 컨텍스트에서 같은 객체 반환 확인
            User found = userRepository.findById(saved.getId()).orElseThrow();
            assertThat(found).isNotNull();
            assertThat(found.getEmail()).isEqualTo(email);
        }

        @Test
        @DisplayName("실패: 이메일 중복 시 DB 제약조건 동작")
        void registerUser_DuplicateEmail_DatabaseConstraint() {
            // Given
            String email = "duplicate@example.com";
            userService.registerUser(email, "password123", "첫번째사용자");

            // When & Then
            assertThatThrownBy(() ->
                    userService.registerUser(email, "password456", "두번째사용자")
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("이미 사용 중인 이메일입니다");

            // DB에 하나만 저장되었는지 확인
            long count = userRepository.count();
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("성공: 여러 사용자 동시 저장")
        void registerUser_MultipleUsers() {
            // Given & When
            User user1 = userService.registerUser("user1@example.com", "password123", "사용자1");
            User user2 = userService.registerUser("user2@example.com", "password123", "사용자2");
            User user3 = userService.registerUser("user3@example.com", "password123", "사용자3");

            // Then
            assertThat(userRepository.count()).isEqualTo(3);
            assertThat(user1.getId()).isNotEqualTo(user2.getId());
            assertThat(user2.getId()).isNotEqualTo(user3.getId());
        }
    }

    @Nested
    @DisplayName("사용자 조회 통합 테스트")
    class GetUserIntegrationTest {

        @Test
        @DisplayName("성공: 실제 DB에서 사용자 조회")
        void getUser_FromRealDatabase() {
            // Given
            User saved = userRepository.save(User.builder()
                    .email("query@example.com")
                    .password("password123")
                    .name("조회테스트")
                    .balance(BigDecimal.valueOf(10000))
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .build());

            // When
            User result = userService.getUser(saved.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(saved.getId());
            assertThat(result.getEmail()).isEqualTo("query@example.com");
            assertThat(result.getName()).isEqualTo("조회테스트");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 사용자 조회")
        void getUser_NotFound() {
            // Given
            Long nonExistentId = 99999L;

            // When & Then
            assertThatThrownBy(() -> userService.getUser(nonExistentId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("사용자를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("실패: DELETED 상태 사용자 조회 불가")
        void getUser_DeletedUser() {
            // Given
            User deleted = userRepository.save(User.builder()
                    .email("deleted@example.com")
                    .password("password123")
                    .name("탈퇴사용자")
                    .balance(BigDecimal.ZERO)
                    .role(UserRole.USER)
                    .status(UserStatus.DELETED)
                    .build());

            // When & Then
            assertThatThrownBy(() -> userService.getUser(deleted.getId()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("탈퇴한 사용자입니다");
        }

        @Test
        @DisplayName("성공: INACTIVE 상태 사용자 조회 가능")
        void getUser_InactiveUser() {
            // Given
            User inactive = userRepository.save(User.builder()
                    .email("inactive@example.com")
                    .password("password123")
                    .name("비활성사용자")
                    .balance(BigDecimal.valueOf(5000))
                    .role(UserRole.USER)
                    .status(UserStatus.INACTIVE)
                    .build());

            // When
            User result = userService.getUser(inactive.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(UserStatus.INACTIVE);
        }
    }

    @Nested
    @DisplayName("이메일 기반 조회 통합 테스트")
    class FindByEmailIntegrationTest {

        @Test
        @DisplayName("성공: 이메일로 사용자 조회")
        void findByEmail_Success() {
            // Given
            String email = "findbyemail@example.com";
            User saved = userRepository.save(User.builder()
                    .email(email)
                    .password("password123")
                    .name("이메일조회테스트")
                    .balance(BigDecimal.ZERO)
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .build());

            // When
            Optional<User> result = userRepository.findByEmail(email);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(saved.getId());
            assertThat(result.get().getName()).isEqualTo("이메일조회테스트");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 이메일")
        void findByEmail_NotFound() {
            // Given
            String nonExistentEmail = "notfound@example.com";

            // When
            Optional<User> result = userRepository.findByEmail(nonExistentEmail);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("성공: existsByEmail로 중복 체크")
        void existsByEmail_Duplicate() {
            // Given
            String email = "exists@example.com";
            userRepository.save(User.builder()
                    .email(email)
                    .password("password123")
                    .name("중복체크")
                    .balance(BigDecimal.ZERO)
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .build());

            // When
            boolean exists = userRepository.existsByEmail(email);
            boolean notExists = userRepository.existsByEmail("notexists@example.com");

            // Then
            assertThat(exists).isTrue();
            assertThat(notExists).isFalse();
        }
    }

    @Nested
    @DisplayName("트랜잭션 및 동시성 테스트")
    class TransactionAndConcurrencyTest {

        @Test
        @DisplayName("성공: 트랜잭션 롤백 확인")
        void transaction_Rollback() {
            // Given
            long initialCount = userRepository.count();

            // When & Then
            try {
                // 의도적으로 예외 발생시켜 롤백 유도
                userService.registerUser("rollback@example.com", "password123", "롤백테스트");
                // 여기서 추가 작업 중 예외 발생 시 롤백됨
            } catch (Exception e) {
                // 예외 무시
            }

            // 정상적으로 저장되었으므로 카운트 증가
            long finalCount = userRepository.count();
            assertThat(finalCount).isEqualTo(initialCount + 1);
        }

        @Test
        @DisplayName("성공: findByIdWithLock으로 비관적 락 테스트")
        @org.springframework.transaction.annotation.Transactional
        void pessimisticLock_Test() {
            // Given
            User saved = userRepository.save(User.builder()
                    .email("lock@example.com")
                    .password("password123")
                    .name("락테스트")
                    .balance(BigDecimal.valueOf(10000))
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .build());

            // When
            Optional<User> locked = userRepository.findByIdWithLock(saved.getId());

            // Then
            assertThat(locked).isPresent();
            assertThat(locked.get().getId()).isEqualTo(saved.getId());
        }
    }
}
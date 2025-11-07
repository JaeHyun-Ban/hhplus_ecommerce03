package com.hhplus.ecommerce.application.user;

import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRole;
import com.hhplus.ecommerce.domain.user.UserStatus;
import com.hhplus.ecommerce.infrastructure.persistence.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

/**
 * UserService 단위 테스트
 *
 * 테스트 전략:
 * - 인메모리 데이터(Map)로 Repository 구현
 * - Given-When-Then 패턴
 * - UseCase 기반 테스트
 */
@DisplayName("UserService 단위 테스트")
class UserServiceTest {

    private UserRepository userRepository;
    private UserService userService;

    /**
     * Fake UserRepository - 인메모리 Map 사용
     */
    static class FakeUserRepository implements UserRepository {
        private final Map<Long, User> store = new HashMap<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Override
        public User save(User user) {
            if (user.getId() == null) {
                Long newId = idGenerator.getAndIncrement();
                User newUser = User.builder()
                        .id(newId)
                        .email(user.getEmail())
                        .password(user.getPassword())
                        .name(user.getName())
                        .balance(user.getBalance())
                        .role(user.getRole())
                        .status(user.getStatus())
                        .build();
                store.put(newId, newUser);
                return newUser;
            } else {
                store.put(user.getId(), user);
                return user;
            }
        }

        @Override
        public Optional<User> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public boolean existsByEmail(String email) {
            return store.values().stream()
                    .anyMatch(user -> user.getEmail().equals(email));
        }

        @Override
        public Optional<User> findByIdWithLock(Long id) {
            return findById(id);
        }

        @Override
        public void delete(User user) {
            store.remove(user.getId());
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        public void clear() {
            store.clear();
            idGenerator.set(1);
        }
    }

    @BeforeEach
    void setUp() {
        FakeUserRepository fakeRepository = new FakeUserRepository();
        fakeRepository.clear();

        userRepository = fakeRepository;
        userService = new UserService(userRepository);
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
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getEmail()).isEqualTo(email);
            assertThat(result.getName()).isEqualTo(name);
            assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getRole()).isEqualTo(UserRole.USER);
            assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);

            // 인메모리에서 확인
            User savedUser = userRepository.findById(1L).orElseThrow();
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

            then(userRepository).should(never()).existsByEmail(any());
            then(userRepository).should(never()).save(any());
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
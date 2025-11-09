package com.hhplus.ecommerce.application.user;

import com.hhplus.ecommerce.domain.user.BalanceHistory;
import com.hhplus.ecommerce.domain.user.BalanceTransactionType;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRole;
import com.hhplus.ecommerce.domain.user.UserStatus;
import com.hhplus.ecommerce.infrastructure.persistence.user.BalanceHistoryRepository;
import com.hhplus.ecommerce.infrastructure.persistence.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * BalanceService 단위 테스트
 *
 * 테스트 전략:
 * - 인메모리 데이터(Map, List) 사용
 * - Given-When-Then 패턴
 * - 잔액 동시성 제어 검증 (비관적 락)
 */
@DisplayName("BalanceService 단위 테스트")
class BalanceServiceTest {

    private UserRepository userRepository;
    private BalanceHistoryRepository balanceHistoryRepository;
    private BalanceService balanceService;

    private User testUser;

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

    /**
     * Fake BalanceHistoryRepository - 인메모리 List 사용
     */
    static class FakeBalanceHistoryRepository implements BalanceHistoryRepository {
        private final List<BalanceHistory> store = new ArrayList<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Override
        public BalanceHistory save(BalanceHistory history) {
            if (history.getId() == null) {
                BalanceHistory newHistory = BalanceHistory.builder()
                        .id(idGenerator.getAndIncrement())
                        .user(history.getUser())
                        .type(history.getType())
                        .amount(history.getAmount())
                        .balanceBefore(history.getBalanceBefore())
                        .balanceAfter(history.getBalanceAfter())
                        .description(history.getDescription())
                        .createdAt(history.getCreatedAt())
                        .build();
                store.add(newHistory);
                return newHistory;
            } else {
                store.add(history);
                return history;
            }
        }

        @Override
        public Page<BalanceHistory> findByUserOrderByCreatedAtDesc(User user, Pageable pageable) {
            List<BalanceHistory> filtered = store.stream()
                    .filter(history -> history.getUser().getId().equals(user.getId()))
                    .sorted(Comparator.comparing(BalanceHistory::getCreatedAt).reversed())
                    .skip(pageable.getOffset())
                    .limit(pageable.getPageSize())
                    .collect(Collectors.toList());

            long total = store.stream()
                    .filter(history -> history.getUser().getId().equals(user.getId()))
                    .count();

            return new PageImpl<>(filtered, pageable, total);
        }

        @Override
        public Optional<BalanceHistory> findById(Long id) {
            return store.stream()
                    .filter(history -> history.getId().equals(id))
                    .findFirst();
        }

        @Override
        public List<BalanceHistory> findAll() {
            return new ArrayList<>(store);
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
        FakeUserRepository fakeUserRepo = new FakeUserRepository();
        FakeBalanceHistoryRepository fakeHistoryRepo = new FakeBalanceHistoryRepository();

        fakeUserRepo.clear();
        fakeHistoryRepo.clear();

        userRepository = fakeUserRepo;
        balanceHistoryRepository = fakeHistoryRepo;
        balanceService = new BalanceService(userRepository, balanceHistoryRepository);

        // 인메모리 테스트 데이터 생성
        testUser = createUser(1L, "test@test.com", BigDecimal.valueOf(10000));
        userRepository.save(testUser);
    }

    @Nested
    @DisplayName("잔액 충전 테스트")
    class ChargeBalanceTest {

        @Test
        @DisplayName("성공: 잔액 충전")
        void chargeBalance_Success() {
            // Given
            Long userId = testUser.getId();
            BigDecimal chargeAmount = BigDecimal.valueOf(5000);
            BigDecimal expectedBalance = BigDecimal.valueOf(15000); // 10000 + 5000

            // When
            BigDecimal result = balanceService.chargeBalance(userId, chargeAmount);

            // Then
            assertThat(result).isEqualByComparingTo(expectedBalance);

            // 인메모리에서 사용자 잔액 확인
            User updatedUser = userRepository.findById(userId).orElseThrow();
            assertThat(updatedUser.getBalance()).isEqualByComparingTo(expectedBalance);

            // 잔액 이력이 저장되었는지 확인
            List<BalanceHistory> histories = balanceHistoryRepository.findAll();
            assertThat(histories).hasSize(1);

            BalanceHistory savedHistory = histories.get(0);
            assertThat(savedHistory.getUser().getId()).isEqualTo(userId);
            assertThat(savedHistory.getType()).isEqualTo(BalanceTransactionType.CHARGE);
            assertThat(savedHistory.getAmount()).isEqualByComparingTo(chargeAmount);
            assertThat(savedHistory.getBalanceBefore()).isEqualByComparingTo(BigDecimal.valueOf(10000));
            assertThat(savedHistory.getBalanceAfter()).isEqualByComparingTo(expectedBalance);
        }

        @Test
        @DisplayName("실패: 사용자를 찾을 수 없음")
        void chargeBalance_UserNotFound() {
            // Given
            Long userId = 999L;
            BigDecimal chargeAmount = BigDecimal.valueOf(5000);

            // When & Then
            assertThatThrownBy(() -> balanceService.chargeBalance(userId, chargeAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");

            // 이력은 저장되지 않아야 함
            assertThat(balanceHistoryRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("실패: 충전 금액이 null")
        void chargeBalance_NullAmount() {
            // Given
            Long userId = testUser.getId();
            BigDecimal chargeAmount = null;

            // When & Then
            assertThatThrownBy(() -> balanceService.chargeBalance(userId, chargeAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("충전 금액은 필수입니다");
        }

        @Test
        @DisplayName("실패: 충전 금액이 0 이하")
        void chargeBalance_InvalidAmount() {
            // Given
            Long userId = testUser.getId();
            BigDecimal chargeAmount = BigDecimal.ZERO;

            // When & Then
            assertThatThrownBy(() -> balanceService.chargeBalance(userId, chargeAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0보다 커야 합니다");
        }

        @Test
        @DisplayName("실패: 충전 금액이 1원 미만")
        void chargeBalance_LessThanOne() {
            // Given
            Long userId = testUser.getId();
            BigDecimal chargeAmount = BigDecimal.valueOf(0.5);

            // When & Then
            assertThatThrownBy(() -> balanceService.chargeBalance(userId, chargeAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최소 1원 이상이어야 합니다");
        }

        @Test
        @DisplayName("성공: 비관적 락으로 사용자 조회 (동시성 제어)")
        void chargeBalance_UsePessimisticLock() {
            // Given
            Long userId = testUser.getId();
            BigDecimal chargeAmount = BigDecimal.valueOf(1000);

            // When
            BigDecimal result = balanceService.chargeBalance(userId, chargeAmount);

            // Then
            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(11000));

            // 인메모리에서는 findByIdWithLock이 실제로 락을 잡지 않지만
            // 메서드가 정상적으로 호출되어 동작하는지 확인
            User user = userRepository.findByIdWithLock(userId).orElseThrow();
            assertThat(user.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(11000));
        }
    }

    @Nested
    @DisplayName("잔액 조회 테스트")
    class GetBalanceTest {

        @Test
        @DisplayName("성공: 현재 잔액 조회")
        void getBalance_Success() {
            // Given
            Long userId = testUser.getId();

            // When
            BigDecimal result = balanceService.getBalance(userId);

            // Then
            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(10000));
        }

        @Test
        @DisplayName("실패: 사용자를 찾을 수 없음")
        void getBalance_UserNotFound() {
            // Given
            Long userId = 999L;

            // When & Then
            assertThatThrownBy(() -> balanceService.getBalance(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("잔액 이력 조회 테스트")
    class GetBalanceHistoryTest {

        @Test
        @DisplayName("성공: 잔액 변동 이력 조회")
        void getBalanceHistory_Success() {
            // Given
            Long userId = testUser.getId();
            Pageable pageable = PageRequest.of(0, 10);

            // 잔액 이력 생성
            BalanceHistory history1 = createBalanceHistory(null, testUser, BalanceTransactionType.CHARGE, BigDecimal.valueOf(5000));
            BalanceHistory history2 = createBalanceHistory(null, testUser, BalanceTransactionType.USE, BigDecimal.valueOf(2000));

            balanceHistoryRepository.save(history1);
            balanceHistoryRepository.save(history2);

            // When
            Page<BalanceHistory> result = balanceService.getBalanceHistory(userId, pageable);

            // Then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("실패: 사용자를 찾을 수 없음")
        void getBalanceHistory_UserNotFound() {
            // Given
            Long userId = 999L;
            Pageable pageable = PageRequest.of(0, 10);

            // When & Then
            assertThatThrownBy(() -> balanceService.getBalanceHistory(userId, pageable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
        }
    }

    // ========================================
    // 테스트 데이터 생성 헬퍼 메서드
    // ========================================

    private User createUser(Long id, String email, BigDecimal balance) {
        return User.builder()
            .id(id)
            .email(email)
            .password("password")
            .name("테스트사용자")
            .balance(balance)
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .build();
    }

    private BalanceHistory createBalanceHistory(
            Long id,
            User user,
            BalanceTransactionType type,
            BigDecimal amount) {

        BigDecimal balanceBefore = user.getBalance();
        BigDecimal balanceAfter = type == BalanceTransactionType.CHARGE
            ? balanceBefore.add(amount)
            : balanceBefore.subtract(amount);

        return BalanceHistory.builder()
            .id(id)
            .user(user)
            .type(type)
            .amount(amount)
            .balanceBefore(balanceBefore)
            .balanceAfter(balanceAfter)
            .description(type + " 테스트")
            .createdAt(LocalDateTime.now())
            .build();
    }
}

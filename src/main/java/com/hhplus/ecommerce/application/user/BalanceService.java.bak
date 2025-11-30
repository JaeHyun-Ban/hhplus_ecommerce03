package com.hhplus.ecommerce.application.user;

import com.hhplus.ecommerce.domain.user.BalanceHistory;
import com.hhplus.ecommerce.domain.user.BalanceTransactionType;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.infrastructure.persistence.user.BalanceHistoryRepository;
import com.hhplus.ecommerce.infrastructure.persistence.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 잔액 애플리케이션 서비스
 *
 * Application Layer - Use Case 실행 계층
 *
 * 책임:
 * - UC-001: 잔액 충전
 * - UC-002: 잔액 조회
 * - 트랜잭션 관리
 * - 잔액 이력 기록
 *
 * 레이어 의존성:
 * - Infrastructure Layer: UserRepository, BalanceHistoryRepository
 * - Domain Layer: User, BalanceHistory
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BalanceService {

    private final UserRepository userRepository;
    private final BalanceHistoryRepository balanceHistoryRepository;

    /**
     * 잔액 충전
     *
     * Use Case: UC-001
     * - Step 1: 사용자가 충전 금액 입력
     * - Step 2: 시스템이 사용자 조회
     * - Step 3: 잔액 충전 (도메인 로직)
     * - Step 4: 잔액 이력 기록
     * - Step 5: 충전 결과 반환
     *
     * 트랜잭션:
     * - 잔액 충전 + 이력 기록은 원자적으로 수행
     * - 비관적 락(PESSIMISTIC_WRITE)으로 동시성 제어
     *
     * 동시성 시나리오:
     * - 사용자 A가 동시에 두 번 충전 요청
     * - SELECT FOR UPDATE로 한 트랜잭션만 실행, 나머지는 대기
     * - 잔액 정확성 보장
     *
     * @param userId 사용자 ID
     * @param amount 충전 금액
     * @return 충전 후 잔액
     * @throws IllegalArgumentException 잘못된 금액
     * @throws EntityNotFoundException 사용자 없음
     */
    @Transactional
    public BigDecimal chargeBalance(Long userId, BigDecimal amount) {
        log.info("[UC-001] 잔액 충전 시작 - userId: {}, amount: {}", userId, amount);

        // Step 1: 입력 검증
        validateChargeAmount(amount);

        // Step 2: 사용자 조회 (비관적 락)
        User user = userRepository.findByIdWithLock(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        // 충전 전 잔액 저장
        BigDecimal balanceBefore = user.getBalance();

        // Step 3: 잔액 충전 (도메인 로직)
        user.chargeBalance(amount);
        BigDecimal balanceAfter = user.getBalance();

        // Step 4: 잔액 이력 기록
        recordBalanceHistory(
            user,
            BalanceTransactionType.CHARGE,
            amount,
            balanceBefore,
            balanceAfter,
            "잔액 충전"
        );

        log.info("[UC-001] 잔액 충전 완료 - userId: {}, before: {}, after: {}",
                 userId, balanceBefore, balanceAfter);

        // Step 5: 충전 후 잔액 반환
        return balanceAfter;
    }

    /**
     * 잔액 조회
     *
     * Use Case: UC-002
     * - Step 1: 사용자 ID로 조회
     * - Step 2: 현재 잔액 반환
     *
     * @param userId 사용자 ID
     * @return 현재 잔액
     * @throws EntityNotFoundException 사용자 없음
     */
    public BigDecimal getBalance(Long userId) {
        log.info("[UC-002] 잔액 조회 - userId: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        return user.getBalance();
    }

    /**
     * 잔액 이력 조회
     *
     * Use Case: UC-002 (확장)
     * - 잔액 변동 이력 조회
     *
     * @param userId 사용자 ID
     * @param pageable 페이징 정보
     * @return 잔액 이력 페이지
     */
    public Page<BalanceHistory> getBalanceHistory(Long userId, Pageable pageable) {
        log.info("[UC-002] 잔액 이력 조회 - userId: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        return balanceHistoryRepository.findByUserOrderByCreatedAtDesc(user, pageable);
    }

    /**
     * 충전 금액 검증
     *
     * 비즈니스 규칙:
     * - 충전 금액은 0보다 커야 함
     * - 충전 금액은 null이 아니어야 함
     * - 충전 금액은 1원 이상이어야 함
     *
     * @param amount 충전 금액
     * @throws IllegalArgumentException 잘못된 금액
     */
    private void validateChargeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("충전 금액은 필수입니다");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다");
        }

        if (amount.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("충전 금액은 최소 1원 이상이어야 합니다");
        }
    }

    /**
     * 잔액 이력 기록
     *
     * 책임:
     * - 모든 잔액 변동에 대한 이력 저장
     * - 감사(Audit) 목적
     *
     * @param user 사용자
     * @param type 거래 유형 (CHARGE, USE, REFUND)
     * @param amount 변동 금액
     * @param balanceBefore 변동 전 잔액
     * @param balanceAfter 변동 후 잔액
     * @param description 설명
     */
    private void recordBalanceHistory(
            User user,
            BalanceTransactionType type,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            String description) {

        BalanceHistory history = BalanceHistory.builder()
            .user(user)
            .type(type)
            .amount(amount)
            .balanceBefore(balanceBefore)
            .balanceAfter(balanceAfter)
            .description(description)
            .createdAt(LocalDateTime.now())
            .build();

        balanceHistoryRepository.save(history);

        log.debug("잔액 이력 기록 - userId: {}, type: {}, amount: {}",
                  user.getId(), type, amount);
    }
}

package com.hhplus.ecommerce.user.application;

import com.hhplus.ecommerce.user.domain.BalanceHistory;
import com.hhplus.ecommerce.user.domain.BalanceTransactionType;
import com.hhplus.ecommerce.user.domain.User;
import com.hhplus.ecommerce.user.infrastructure.persistence.BalanceHistoryRepository;
import com.hhplus.ecommerce.user.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

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
 * - 동시성 제어 (Redisson 분산 락)
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
    private final RedissonClient redissonClient;
    private final org.springframework.context.ApplicationContext applicationContext;

    private static final String BALANCE_LOCK_PREFIX = "balance:user:lock:";
    private static final long LOCK_WAIT_TIME = 10L;     // 락 획득 대기 시간 (초)
    private static final long LOCK_LEASE_TIME = 10L;    // 락 자동 해제 시간 (초)

    /**
     * 잔액 충전
     *
     * Use Case: UC-001
     * - Step 1: 사용자가 충전 금액 입력
     * - Step 2: 분산 락 획득
     * - Step 3: 새 트랜잭션에서 잔액 충전 실행 (REQUIRES_NEW)
     * - Step 4: 트랜잭션 커밋 (중요: 락 해제 전에 자동 커밋)
     * - Step 5: 분산 락 해제
     * - Step 6: 충전 결과 반환
     *
     * 트랜잭션:
     * - chargeBalanceWithLock()이 REQUIRES_NEW로 독립 트랜잭션 생성
     * - 메소드 반환 시 자동 커밋, 그 후 finally에서 락 해제
     * - Redisson 분산 락으로 동시성 제어
     *
     * 동시성 시나리오:
     * - 사용자 A가 동시에 두 번 충전 요청
     * - 분산 락으로 한 트랜잭션만 실행, 나머지는 대기
     * - 트랜잭션 커밋 후 락 해제로 다음 트랜잭션이 최신 데이터 조회
     * - 잔액 정확성 보장
     *
     * @param userId 사용자 ID
     * @param amount 충전 금액
     * @return 충전 후 잔액
     * @throws IllegalArgumentException 잘못된 금액
     * @throws IllegalStateException 락 획득 실패
     */
    public BigDecimal chargeBalance(Long userId, BigDecimal amount) {
        log.info("[UC-001] 잔액 충전 시작 - userId: {}, amount: {}", userId, amount);

        // Step 1: 입력 검증
        validateChargeAmount(amount);

        // Step 2: 분산 락 획득
        String lockKey = BALANCE_LOCK_PREFIX + userId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 락 획득 시도: 10초 대기, 10초 후 자동 해제
            boolean isLocked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

            if (!isLocked) {
                log.warn("잔액 충전 락 획득 실패 - userId: {}", userId);
                throw new IllegalStateException("잔액 처리 요청이 많습니다. 잠시 후 다시 시도해주세요");
            }

            log.debug("분산 락 획득 성공 - lockKey: {}", lockKey);

            try {
                // Step 3: 새 트랜잭션에서 잔액 충전 (REQUIRES_NEW로 독립 트랜잭션)
                // 프록시를 통해 호출하여 @Transactional 적용
                // 메소드 반환 시 자동 커밋 → finally에서 락 해제
                BalanceService self = applicationContext.getBean(BalanceService.class);
                return self.chargeBalanceWithLock(userId, amount);

            } finally {
                // Step 5: 락 해제 (트랜잭션 커밋 후 실행)
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                    log.debug("분산 락 해제 완료 - lockKey: {}", lockKey);
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("잔액 충전 중 인터럽트 발생 - userId: {}", userId, e);
            throw new IllegalStateException("잔액 충전 중 오류가 발생했습니다");
        }
    }

    /**
     * 락을 획득한 상태에서 잔액 충전 수행
     *
     * REQUIRES_NEW: 독립적인 새 트랜잭션 생성
     * - 메소드 반환 시 트랜잭션 자동 커밋
     * - chargeBalance()의 finally 블록에서 락 해제
     * - 트랜잭션 커밋 → 락 해제 순서 보장으로 동시성 정합성 확보
     *
     * 주의: 이 메소드는 반드시 Spring 프록시를 통해 호출되어야 함
     * chargeBalance()에서 applicationContext.getBean()으로 프록시 획득 후 호출
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BigDecimal chargeBalanceWithLock(Long userId, BigDecimal amount) {
        // 사용자 조회
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        // 충전 전 잔액 저장
        BigDecimal balanceBefore = user.getBalance();

        // 잔액 충전 (도메인 로직)
        user.chargeBalance(amount);
        userRepository.save(user);  // 명시적 저장
        BigDecimal balanceAfter = user.getBalance();

        // 잔액 이력 기록
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

        // 트랜잭션 커밋 (메소드 반환 시 자동)
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
     * @throws IllegalArgumentException 사용자 없음
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

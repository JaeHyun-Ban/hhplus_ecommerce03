package com.hhplus.ecommerce.application.coupon;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.infrastructure.persistence.coupon.CouponRepository;
import com.hhplus.ecommerce.infrastructure.persistence.coupon.UserCouponRepository;
import com.hhplus.ecommerce.infrastructure.persistence.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 쿠폰 애플리케이션 서비스
 *
 * Application Layer - Use Case 실행 계층
 *
 * 책임:
 * - UC-017: 선착순 쿠폰 발급
 * - UC-018: 발급 가능한 쿠폰 목록 조회
 * - UC-019: 내 쿠폰 목록 조회
 * - 트랜잭션 관리
 * - 동시성 제어 (낙관적 락 + 재시도)
 *
 * 레이어 의존성:
 * - Infrastructure Layer: CouponRepository, UserCouponRepository, UserRepository
 * - Domain Layer: Coupon, UserCoupon, User
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;

    /**
     * 선착순 쿠폰 발급
     *
     * Use Case: UC-017
     *
     * Main Success Scenario:
     * 1. 사용자 조회
     * 2. 쿠폰 조회 (낙관적 락)
     * 3. 발급 가능 여부 확인
     *    - 발급 기간 확인
     *    - 남은 수량 확인
     *    - 쿠폰 상태 확인
     * 4. 1인당 발급 제한 확인
     * 5. 쿠폰 발급 (issuedQuantity++, version++ 원자적 수행)
     * 6. 사용자 쿠폰 생성
     * 7. 발급 완료 반환
     *
     * 동시성 제어:
     * - 낙관적 락 (@Version)으로 동시 발급 제어
     * - 충돌 시 최대 3회 자동 재시도
     * - 100ms 간격으로 재시도 (Exponential Backoff)
     *
     * 동시성 시나리오 (선착순 100개 쿠폰, 1000명 동시 요청):
     * - 1000개 트랜잭션이 동시에 SELECT (모두 issuedQuantity=0 읽음)
     * - 첫 번째 UPDATE만 성공 (version 0→1, issuedQuantity 0→1)
     * - 나머지 999개는 OptimisticLockException 발생
     * - @Retryable로 자동 재시도 (최대 3회)
     * - 재시도 시 최신 version으로 다시 SELECT 후 UPDATE
     * - 최종적으로 정확히 100명만 발급 성공
     *
     * Extensions (예외 시나리오):
     * 3a. 발급 기간이 아닌 경우
     *     - IllegalStateException: "쿠폰 발급 기간이 아닙니다"
     * 3b. 쿠폰이 모두 소진된 경우
     *     - IllegalStateException: "쿠폰이 모두 소진되었습니다"
     * 4a. 이미 최대 발급 수량을 받은 경우
     *     - IllegalStateException: "이미 최대 발급 수량을 받았습니다"
     * 5a. 낙관적 락 충돌 (동시 발급 시도)
     *     - OptimisticLockingFailureException → 자동 재시도
     * 5b. 재시도 3회 모두 실패
     *     - OptimisticLockingFailureException → Controller에서 409 Conflict 응답
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 발급된 사용자 쿠폰
     * @throws IllegalArgumentException 사용자 또는 쿠폰을 찾을 수 없음
     * @throws IllegalStateException 발급 불가 (기간, 수량, 제한)
     * @throws OptimisticLockingFailureException 동시성 충돌 (재시도 실패)
     */
    @Transactional
    @Retryable(
        value = OptimisticLockingFailureException.class,  // 이 예외 발생 시 재시도
        maxAttempts = 3,                                   // 최대 3회 시도
        backoff = @Backoff(delay = 100)                    // 100ms 대기 후 재시도
    )
    public UserCoupon issueCoupon(Long userId, Long couponId) {
        log.info("[UC-017] 선착순 쿠폰 발급 시작 - userId: {}, couponId: {}", userId, couponId);

        // Step 1: 사용자 조회
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        // Step 2: 쿠폰 조회 (낙관적 락)
        // → SELECT * FROM coupons WHERE id = ? (현재 version 값을 읽음)
        Coupon coupon = couponRepository.findByIdWithLock(couponId)
            .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다. ID: " + couponId));

        log.debug("쿠폰 조회 완료 - 남은 수량: {}/{}, version: {}",
                  coupon.getTotalQuantity() - coupon.getIssuedQuantity(),
                  coupon.getTotalQuantity(),
                  coupon.getVersion());

        // Step 3: 발급 가능 여부 확인
        if (!coupon.canIssue()) {
            // 구체적인 실패 사유 확인
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(coupon.getIssueStartAt())) {
                throw new IllegalStateException("쿠폰 발급 기간이 아닙니다. 발급 시작: " + coupon.getIssueStartAt());
            }
            if (now.isAfter(coupon.getIssueEndAt())) {
                throw new IllegalStateException("쿠폰 발급 기간이 종료되었습니다. 종료일: " + coupon.getIssueEndAt());
            }
            if (coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
                throw new IllegalStateException("쿠폰이 모두 소진되었습니다");
            }
            throw new IllegalStateException("쿠폰을 발급할 수 없습니다");
        }

        // Step 4: 1인당 발급 제한 확인
        Long userIssuedCount = userCouponRepository.countByUserAndCoupon(user, coupon);
        if (userIssuedCount >= coupon.getMaxIssuePerUser()) {
            throw new IllegalStateException(
                String.format("이미 최대 발급 수량을 받았습니다. (발급 횟수: %d/%d)",
                              userIssuedCount, coupon.getMaxIssuePerUser())
            );
        }

        // Step 5: 쿠폰 발급 (원자적 수행)
        // → UPDATE coupons SET issued_quantity = ?, version = version + 1
        //    WHERE id = ? AND version = ? (읽었던 version과 일치해야만 성공)
        // → 다른 트랜잭션이 먼저 UPDATE했다면 version 불일치로 실패
        //    → OptimisticLockingFailureException 발생 → @Retryable로 재시도
        try {
            coupon.issue();
            log.debug("쿠폰 발급 처리 완료 - 발급 수량: {}/{}", coupon.getIssuedQuantity(), coupon.getTotalQuantity());
        } catch (IllegalStateException e) {
            // 발급 중 수량이 소진된 경우 (동시 요청으로 인한 Race Condition)
            log.warn("쿠폰 발급 실패 - couponId: {}, reason: {}", couponId, e.getMessage());
            throw e;
        }

        // Step 6: 사용자 쿠폰 생성
        UserCoupon userCoupon = UserCoupon.builder()
            .user(user)
            .coupon(coupon)
            .status(UserCouponStatus.ISSUED)
            .issuedAt(LocalDateTime.now())
            .build();

        UserCoupon savedUserCoupon = userCouponRepository.save(userCoupon);

        log.info("[UC-017] 쿠폰 발급 완료 - userId: {}, couponId: {}, userCouponId: {}",
                 userId, couponId, savedUserCoupon.getId());

        // Step 7: 발급 완료 반환
        return savedUserCoupon;
    }

    /**
     * 발급 가능한 쿠폰 목록 조회
     *
     * Use Case: UC-018
     *
     * Main Success Scenario:
     * 1. 현재 발급 가능한 쿠폰 목록 조회
     *    - 발급 기간 내
     *    - 남은 수량이 있는 쿠폰
     *    - 활성 상태 쿠폰
     * 2. 쿠폰 목록 반환
     *
     * @return 발급 가능한 쿠폰 목록
     */
    public List<Coupon> getAvailableCoupons() {
        log.info("[UC-018] 발급 가능한 쿠폰 목록 조회");

        LocalDateTime now = LocalDateTime.now();
        List<Coupon> coupons = couponRepository.findAvailableCoupons(now);

        log.info("발급 가능한 쿠폰 개수: {}", coupons.size());
        return coupons;
    }

    /**
     * 내 쿠폰 목록 조회 (전체)
     *
     * Use Case: UC-019
     *
     * Main Success Scenario:
     * 1. 사용자 조회
     * 2. 사용자의 모든 쿠폰 조회 (발급일시 최신순)
     * 3. 쿠폰 목록 반환
     *
     * @param userId 사용자 ID
     * @return 사용자의 모든 쿠폰 목록 (사용 완료, 만료 포함)
     * @throws IllegalArgumentException 사용자를 찾을 수 없음
     */
    public List<UserCoupon> getMyCoupons(Long userId) {
        log.info("[UC-019] 내 쿠폰 목록 조회 - userId: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        List<UserCoupon> userCoupons = userCouponRepository.findByUserOrderByIssuedAtDesc(user);

        log.info("사용자 쿠폰 개수: {}", userCoupons.size());
        return userCoupons;
    }

    /**
     * 사용 가능한 내 쿠폰 목록 조회
     *
     * Use Case: UC-019 (확장)
     * - 주문 시 적용 가능한 쿠폰만 조회
     *
     * Main Success Scenario:
     * 1. 사용자 조회
     * 2. 사용 가능한 쿠폰 조회
     *    - status = ISSUED
     *    - 유효 기간 내
     * 3. 쿠폰 목록 반환
     *
     * @param userId 사용자 ID
     * @return 사용 가능한 쿠폰 목록
     * @throws IllegalArgumentException 사용자를 찾을 수 없음
     */
    public List<UserCoupon> getAvailableMyCoupons(Long userId) {
        log.info("[UC-019] 사용 가능한 내 쿠폰 목록 조회 - userId: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        LocalDateTime now = LocalDateTime.now();
        List<UserCoupon> availableCoupons = userCouponRepository.findAvailableCouponsByUser(user, now);

        log.info("사용 가능한 쿠폰 개수: {}", availableCoupons.size());
        return availableCoupons;
    }

    /**
     * 쿠폰 상세 조회
     *
     * Use Case: UC-018 (확장)
     * - 쿠폰 상세 정보 조회
     *
     * @param couponId 쿠폰 ID
     * @return 쿠폰 상세 정보
     * @throws IllegalArgumentException 쿠폰을 찾을 수 없음
     */
    public Coupon getCoupon(Long couponId) {
        log.info("[UC-018] 쿠폰 상세 조회 - couponId: {}", couponId);

        return couponRepository.findById(couponId)
            .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다. ID: " + couponId));
    }
}
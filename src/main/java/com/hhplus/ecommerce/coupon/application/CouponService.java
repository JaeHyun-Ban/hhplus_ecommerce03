package com.hhplus.ecommerce.coupon.application;

import com.hhplus.ecommerce.coupon.domain.Coupon;
import com.hhplus.ecommerce.coupon.domain.UserCoupon;
import com.hhplus.ecommerce.coupon.domain.UserCouponStatus;
import com.hhplus.ecommerce.user.domain.User;
import com.hhplus.ecommerce.coupon.infrastructure.persistence.CouponRepository;
import com.hhplus.ecommerce.coupon.infrastructure.persistence.UserCouponRepository;
import com.hhplus.ecommerce.user.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
 * - 동시성 제어 (Redisson 분산 락)
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
    private final RedissonClient redissonClient;

    private static final String COUPON_LOCK_PREFIX = "coupon:issue:lock:";
    private static final long LOCK_WAIT_TIME = 10L;     // 락 획득 대기 시간 (초)
    private static final long LOCK_LEASE_TIME = 10L;    // 락 자동 해제 시간 (초)

    /**
     * 선착순 쿠폰 발급
     *
     * Use Case: UC-017
     *
     * Main Success Scenario:
     * 1. 분산 락 획득 (Redisson RLock)
     * 2. 사용자 조회
     * 3. 쿠폰 조회
     * 4. 발급 가능 여부 확인
     *    - 발급 기간 확인
     *    - 남은 수량 확인
     *    - 쿠폰 상태 확인
     * 5. 1인당 발급 제한 확인
     * 6. 쿠폰 발급 (issuedQuantity++ 원자적 수행)
     * 7. 사용자 쿠폰 생성
     * 8. 분산 락 해제
     * 9. 발급 완료 반환
     *
     * 동시성 제어:
     * - Redisson 분산 락으로 동시 발급 제어
     * - 락 키: coupon:issue:lock:{couponId}
     * - 락 대기 시간: 5초
     * - 락 자동 해제: 3초 (Watchdog 자동 갱신)
     *
     * 동시성 시나리오 (선착순 100개 쿠폰, 1000명 동시 요청):
     * - 1000개 요청이 동시에 락 획득 시도
     * - 첫 번째 요청만 락 획득 성공, 나머지는 대기
     * - 락을 획득한 요청이 쿠폰 발급 후 락 해제
     * - 다음 대기 중인 요청이 락 획득
     * - 순차적으로 100명만 발급 성공, 나머지는 수량 소진으로 실패
     *
     * Extensions (예외 시나리오):
     * 1a. 락 획득 실패 (대기 시간 초과)
     *     - IllegalStateException: "쿠폰 발급 요청이 많습니다. 잠시 후 다시 시도해주세요"
     * 4a. 발급 기간이 아닌 경우
     *     - IllegalStateException: "쿠폰 발급 기간이 아닙니다"
     * 4b. 쿠폰이 모두 소진된 경우
     *     - IllegalStateException: "쿠폰이 모두 소진되었습니다"
     * 5a. 이미 최대 발급 수량을 받은 경우
     *     - IllegalStateException: "이미 최대 발급 수량을 받았습니다"
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 발급된 사용자 쿠폰
     * @throws IllegalArgumentException 사용자 또는 쿠폰을 찾을 수 없음
     * @throws IllegalStateException 발급 불가 (기간, 수량, 제한, 락 획득 실패)
     */
    @Transactional
    public UserCoupon issueCoupon(Long userId, Long couponId) {
        log.info("[UC-017] 선착순 쿠폰 발급 시작 - userId: {}, couponId: {}", userId, couponId);

        // Step 1: 분산 락 획득
        String lockKey = COUPON_LOCK_PREFIX + couponId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 락 획득 시도: 5초 대기, 3초 후 자동 해제
            boolean isLocked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

            if (!isLocked) {
                log.warn("쿠폰 발급 락 획득 실패 - userId: {}, couponId: {}", userId, couponId);
                throw new IllegalStateException("쿠폰 발급 요청이 많습니다. 잠시 후 다시 시도해주세요");
            }

            log.debug("분산 락 획득 성공 - lockKey: {}", lockKey);

            try {
                return issueCouponWithLock(userId, couponId);
            } finally {
                // 락 해제 (반드시 실행)
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                    log.debug("분산 락 해제 완료 - lockKey: {}", lockKey);
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("쿠폰 발급 중 인터럽트 발생 - userId: {}, couponId: {}", userId, couponId, e);
            throw new IllegalStateException("쿠폰 발급 중 오류가 발생했습니다");
        }
    }

    /**
     * 락을 획득한 상태에서 쿠폰 발급 수행
     */
    private UserCoupon issueCouponWithLock(Long userId, Long couponId) {
        // Step 2: 사용자 조회
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        // Step 3: 쿠폰 조회
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다. ID: " + couponId));

        log.debug("쿠폰 조회 완료 - 남은 수량: {}/{}",
                  coupon.getTotalQuantity() - coupon.getIssuedQuantity(),
                  coupon.getTotalQuantity());

        // Step 4: 발급 가능 여부 확인
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

        // Step 5: 1인당 발급 제한 확인
        Long userIssuedCount = userCouponRepository.countByUserAndCoupon(user, coupon);
        if (userIssuedCount >= coupon.getMaxIssuePerUser()) {
            throw new IllegalStateException(
                String.format("이미 최대 발급 수량을 받았습니다. (발급 횟수: %d/%d)",
                              userIssuedCount, coupon.getMaxIssuePerUser())
            );
        }

        // Step 6: 쿠폰 발급 (원자적 수행)
        // 분산 락으로 보호되므로 동시성 문제 없음
        try {
            coupon.issue();
            couponRepository.save(coupon);  // 명시적 저장
            log.debug("쿠폰 발급 처리 완료 - 발급 수량: {}/{}", coupon.getIssuedQuantity(), coupon.getTotalQuantity());
        } catch (IllegalStateException e) {
            // 발급 중 수량이 소진된 경우
            log.warn("쿠폰 발급 실패 - couponId: {}, reason: {}", couponId, e.getMessage());
            throw e;
        }

        // Step 7: 사용자 쿠폰 생성
        UserCoupon userCoupon = UserCoupon.builder()
            .user(user)
            .coupon(coupon)
            .status(UserCouponStatus.ISSUED)
            .issuedAt(LocalDateTime.now())
            .build();

        UserCoupon savedUserCoupon = userCouponRepository.save(userCoupon);

        log.info("[UC-017] 쿠폰 발급 완료 - userId: {}, couponId: {}, userCouponId: {}",
                 userId, couponId, savedUserCoupon.getId());

        // Step 8: 발급 완료 반환
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

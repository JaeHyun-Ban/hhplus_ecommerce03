package com.hhplus.ecommerce.coupon.application;

import com.hhplus.ecommerce.coupon.domain.Coupon;
import com.hhplus.ecommerce.coupon.domain.UserCoupon;
import com.hhplus.ecommerce.coupon.domain.UserCouponStatus;
import com.hhplus.ecommerce.coupon.domain.event.CouponIssuedEvent;
import com.hhplus.ecommerce.user.domain.User;
import com.hhplus.ecommerce.coupon.infrastructure.persistence.CouponRepository;
import com.hhplus.ecommerce.coupon.infrastructure.persistence.UserCouponRepository;
import com.hhplus.ecommerce.user.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 쿠폰 이벤트 핸들러 (비동기 DB 동기화)
 *
 * Redis에서 발급된 쿠폰 정보를 비동기로 DB에 저장
 *
 * 처리 흐름:
 * 1. CouponService에서 Redis 발급 성공 시 CouponIssuedEvent 발행
 * 2. @TransactionalEventListener가 이벤트 수신 (트랜잭션 커밋 후)
 * 3. @Async로 별도 스레드에서 DB 저장 수행
 * 4. 실패 시 @Retryable로 최대 5회 재시도
 *
 * 장점:
 * - Redis 성공 = 사용자 성공 (즉시 응답)
 * - DB Deadlock이 사용자 경험에 영향 없음
 * - 자동 재시도로 최종 일관성 보장
 *
 * 동시성 제어:
 * - Redis: Sorted Set + Lua Script (선착순 보장)
 * - DB: 비동기 저장 + Retry (최종 일관성)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponEventHandler {

    private final UserCouponRepository userCouponRepository;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;

    /**
     * 쿠폰 발급 이벤트 처리 (비동기 DB 동기화)
     *
     * @TransactionalEventListener:
     * - phase = AFTER_COMMIT: 이벤트 발행 트랜잭션이 커밋된 후 실행
     * - fallbackExecution = true: 트랜잭션 없이 이벤트 발행해도 실행
     *
     * @Async("couponEventExecutor"):
     * - 별도 Thread Pool에서 비동기 실행
     * - 호출 스레드는 즉시 반환 (사용자 응답 빠름)
     *
     * @Retryable:
     * - DB Deadlock/Lock 실패 시 자동 재시도
     * - maxAttempts: 5회
     * - backoff: 100ms → 150ms → 225ms → 337ms → 500ms
     *
     * @Transactional(propagation = REQUIRES_NEW):
     * - 새로운 트랜잭션 시작 (부모 트랜잭션과 독립)
     * - 재시도 시마다 새로운 트랜잭션 생성
     *
     * @param event 쿠폰 발급 이벤트
     */
    @Async("couponEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Retryable(
        include = {
            DeadlockLoserDataAccessException.class,
            CannotAcquireLockException.class,
            DataIntegrityViolationException.class,
            JpaSystemException.class
        },
        maxAttempts = 5,
        backoff = @Backoff(delay = 100, multiplier = 1.5, maxDelay = 500)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleCouponIssued(CouponIssuedEvent event) {
        log.info("[비동기] 쿠폰 발급 DB 동기화 시작 - {}", event);

        try {
            // 1. 엔티티 조회
            Coupon coupon = couponRepository.findById(event.getCouponId())
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다. ID: " + event.getCouponId()));

            User user = userRepository.findById(event.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + event.getUserId()));

            // 2. 중복 발급 체크 (멱등성 보장)
            Long issuedCount = userCouponRepository.countByUserAndCoupon(user, coupon);
            if (issuedCount > 0) {
                log.warn("[비동기] 이미 발급된 쿠폰 - userId: {}, couponId: {}, 발급 횟수: {}",
                         event.getUserId(), event.getCouponId(), issuedCount);
                return;
            }

            // 3. 사용자 쿠폰 생성 및 저장
            UserCoupon userCoupon = UserCoupon.builder()
                .user(user)
                .coupon(coupon)
                .status(UserCouponStatus.ISSUED)
                .issuedAt(event.getOccurredAt())
                .build();

            UserCoupon savedUserCoupon = userCouponRepository.save(userCoupon);

            // 4. 쿠폰 발급 수량 증가 (도메인 로직)
            coupon.issue();
            couponRepository.save(coupon);

            log.info("[비동기] 쿠폰 발급 DB 동기화 완료 - userId: {}, couponId: {}, userCouponId: {}, rank: {}, issuedCount: {}",
                     event.getUserId(), event.getCouponId(), savedUserCoupon.getId(), event.getRank(), event.getIssuedCount());

        } catch (DeadlockLoserDataAccessException | CannotAcquireLockException e) {
            log.warn("[비동기] DB Lock 실패, 재시도 예정 - userId: {}, couponId: {}, error: {}",
                     event.getUserId(), event.getCouponId(), e.getMessage());
            throw e; // @Retryable이 재시도

        } catch (DataIntegrityViolationException e) {
            log.warn("[비동기] DB 제약조건 위반 (중복 발급 가능성) - userId: {}, couponId: {}, error: {}",
                     event.getUserId(), event.getCouponId(), e.getMessage());
            // 중복 발급은 재시도 불필요

        } catch (Exception e) {
            log.error("[비동기] 쿠폰 발급 DB 동기화 실패 - userId: {}, couponId: {}, error: {}",
                      event.getUserId(), event.getCouponId(), e.getMessage(), e);
            throw e;
        }
    }
}

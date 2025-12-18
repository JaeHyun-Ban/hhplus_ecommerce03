package com.hhplus.ecommerce.coupon.application;

import com.hhplus.ecommerce.coupon.domain.Coupon;
import com.hhplus.ecommerce.coupon.domain.UserCoupon;
import com.hhplus.ecommerce.coupon.domain.UserCouponStatus;
import com.hhplus.ecommerce.coupon.domain.event.CouponIssuedEvent;
import com.hhplus.ecommerce.user.domain.User;
import com.hhplus.ecommerce.coupon.infrastructure.persistence.CouponRedisRepository;
import com.hhplus.ecommerce.coupon.infrastructure.persistence.CouponRepository;
import com.hhplus.ecommerce.coupon.infrastructure.persistence.UserCouponRepository;
import com.hhplus.ecommerce.user.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 쿠폰 애플리케이션 서비스 (Kafka 기반 비동기)
 *
 * Application Layer - Use Case 실행 계층
 *
 * 책임:
 * - UC-017: 선착순 쿠폰 발급 (Redis 기반 + Kafka 비동기 DB 동기화)
 * - UC-018: 발급 가능한 쿠폰 목록 조회
 * - UC-019: 내 쿠폰 목록 조회
 * - 트랜잭션 관리
 * - Kafka 이벤트 발행
 *
 * Kafka 비동기 아키텍처:
 * - Redis 발급 성공 → 즉시 응답 (사용자 대기 시간 최소화)
 * - Kafka Topic(coupon-events)으로 CouponIssuedEvent 발행
 * - Kafka Consumer가 메시지 수신 → 비동기 DB 저장
 * - DB Deadlock이 사용자 경험에 영향 없음
 * - Kafka의 재시도 및 DLQ로 안정성 보장
 *
 * 레이어 의존성:
 * - Infrastructure Layer: CouponRepository, UserCouponRepository, UserRepository
 * - Domain Layer: Coupon, UserCoupon, User, CouponIssuedEvent
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    private final CouponRedisRepository couponRedisRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 선착순 쿠폰 발급 (Redis 기반 + Kafka 비동기 DB 동기화)
     *
     * Use Case: UC-017
     *
     * Main Success Scenario:
     * 1. 쿠폰 조회 (DB - 캐시 가능)
     * 2. 발급 기간 검증
     * 3. Redis 원자적 연산으로 발급 처리
     *    - Sorted Set + Lua Script로 선착순 보장
     *    - 사용자별 발급 제한 검증
     *    - 총 수량 제한 검증
     * 4. Redis 성공 시 즉시 응답 (DB 저장 대기 없음)
     * 5. Kafka로 CouponIssuedEvent 발행 → Consumer가 비동기 DB 저장
     *
     * Kafka 비동기 아키텍처 장점:
     * - **즉시 응답**: Redis 성공 = 사용자 성공 (응답 시간 최소화)
     * - **DB 병목 제거**: DB Deadlock이 사용자 경험에 영향 없음
     * - **최종 일관성**: Redis(Source of Truth) → DB는 나중에 동기화
     * - **자동 재시도**: Kafka Consumer에서 실패 시 재시도
     * - **확장성**: Consumer 인스턴스 수평 확장 가능
     * - **모니터링**: Kafka Lag으로 처리 상태 추적
     *
     * 동시성 제어:
     * - Redis: Sorted Set + Lua Script (선착순 정확히 100명 선택)
     * - Kafka: 파티션별 순서 보장 (couponId 기반 파티셔닝)
     * - DB: Consumer에서 비동기 저장 + 재시도 (최종 일관성)
     *
     * 동시성 시나리오 (선착순 100개 쿠폰, 120명 동시 요청):
     * - 120개 요청이 Redis에 동시 발급 시도
     * - Redis: 정확히 100명 선택 → 즉시 성공 응답
     * - 나머지 20명: 즉시 실패 응답 (빠른 실패)
     * - 비동기: 100개 이벤트 Kafka 발행 → Consumer가 DB 저장
     * - DB Deadlock 발생해도 Consumer 재시도 (사용자는 이미 성공 응답 받음)
     *
     * Extensions (예외 시나리오):
     * 2a. 발급 기간이 아닌 경우
     *     - IllegalStateException: "쿠폰 발급 기간이 아닙니다"
     * 3a. 쿠폰이 모두 소진된 경우 (Redis)
     *     - IllegalStateException: "쿠폰이 모두 소진되었습니다"
     * 3b. 이미 최대 발급 수량을 받은 경우 (Redis)
     *     - IllegalStateException: "이미 최대 발급 수량을 받았습니다"
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 발급된 사용자 쿠폰 (임시 객체, DB 저장 전)
     * @throws IllegalArgumentException 쿠폰을 찾을 수 없음
     * @throws IllegalStateException 발급 불가 (기간, 수량, 제한)
     */
    @Transactional
    public UserCoupon issueCoupon(Long userId, Long couponId) {
        log.info("[UC-017] 선착순 쿠폰 발급 시작 (비동기) - userId: {}, couponId: {}", userId, couponId);

        // Step 1: 쿠폰 조회 (DB)
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다. ID: " + couponId));

        // Step 2: 발급 기간 검증
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getIssueStartAt())) {
            throw new IllegalStateException("쿠폰 발급 기간이 아닙니다. 발급 시작: " + coupon.getIssueStartAt());
        }
        if (now.isAfter(coupon.getIssueEndAt())) {
            throw new IllegalStateException("쿠폰 발급 기간이 종료되었습니다. 종료일: " + coupon.getIssueEndAt());
        }

        // Step 3: Redis 원자적 연산으로 발급 처리 (Sorted Set + Lua Script)
        CouponRedisRepository.IssueResult issueResult = couponRedisRepository.issue(
            couponId,
            userId,
            coupon.getTotalQuantity(),
            coupon.getMaxIssuePerUser()
        );

        if (!issueResult.isSuccess()) {
            // Redis에서 발급 실패 (수량 소진 또는 사용자 제한 초과)
            String failReason = issueResult.getMessage();

            if ("SOLD_OUT".equals(failReason)) {
                throw new IllegalStateException("쿠폰이 모두 소진되었습니다");
            } else if ("EXCEED_USER_LIMIT".equals(failReason)) {
                Long userCount = couponRedisRepository.getUserIssuedCount(couponId, userId);
                throw new IllegalStateException(
                    String.format("이미 최대 발급 수량을 받았습니다. (발급 횟수: %d/%d)",
                                  userCount, coupon.getMaxIssuePerUser())
                );
            } else if ("ALREADY_ISSUED".equals(failReason)) {
                throw new IllegalStateException("이미 발급받은 쿠폰입니다");
            } else {
                throw new IllegalStateException("쿠폰 발급에 실패했습니다: " + failReason);
            }
        }

        // Step 4: Redis 성공 → Kafka 이벤트 발행 (비동기 DB 저장)
        CouponIssuedEvent event = CouponIssuedEvent.of(
            couponId,
            userId,
            issueResult.getRank(),
            issueResult.getIssuedCount()
        );

        // Kafka로 이벤트 발행 (couponId를 파티션 키로 사용 → 동일 쿠폰은 동일 파티션에서 순서 보장)
        kafkaTemplate.send("coupon-events", couponId.toString(), event);

        log.info("[UC-017] 쿠폰 발급 성공 (Redis) - userId: {}, couponId: {}, rank: {}, issued: {}/{}, Kafka 발행 완료",
                 userId, couponId, issueResult.getRank(), issueResult.getIssuedCount(), coupon.getTotalQuantity());

        // Step 5: 즉시 응답 반환 (임시 UserCoupon 객체)
        // 실제 DB 저장은 Kafka Consumer에서 비동기로 처리
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        return UserCoupon.builder()
            .user(user)
            .coupon(coupon)
            .status(UserCouponStatus.ISSUED)
            .issuedAt(LocalDateTime.now())
            .build();
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

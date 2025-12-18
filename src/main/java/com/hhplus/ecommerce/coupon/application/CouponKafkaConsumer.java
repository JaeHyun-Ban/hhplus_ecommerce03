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
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 Kafka Consumer (비동기 DB 동기화)
 *
 * Kafka Topic: coupon-events
 * Consumer Group: coupon-consumer-group
 *
 * Redis에서 발급된 쿠폰 정보를 Kafka를 통해 수신하여 비동기로 DB에 저장
 *
 * 처리 흐름:
 * 1. CouponService에서 Redis 발급 성공 시 Kafka로 CouponIssuedEvent 발행
 * 2. @KafkaListener가 메시지 수신
 * 3. DB 저장 수행
 * 4. 성공 시 수동 커밋 (ack)
 * 5. 실패 시 @Retryable로 최대 5회 재시도
 * 6. 최종 실패 시 DLQ(Dead Letter Queue)로 전송
 *
 * Kafka 아키텍처 장점:
 * - Redis 성공 = 사용자 성공 (즉시 응답)
 * - DB Deadlock이 사용자 경험에 영향 없음
 * - 자동 재시도 및 DLQ로 안정성 보장
 * - Consumer 인스턴스 수평 확장 가능
 * - Kafka Lag으로 모니터링 가능
 *
 * 동시성 제어:
 * - Kafka: 파티션별 순서 보장 (couponId 기반 파티셔닝)
 * - DB: 비동기 저장 + Retry (최종 일관성)
 * - 멱등성: countByUserAndCoupon으로 중복 발급 방지
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponKafkaConsumer {

    private final UserCouponRepository userCouponRepository;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;

    /**
     * 쿠폰 발급 이벤트 처리 (Kafka Consumer)
     *
     * @KafkaListener:
     * - topics: coupon-events 토픽 구독
     * - groupId: coupon-consumer-group (동일 그룹 내에서 메시지 분산 처리)
     * - containerFactory: 수동 커밋 모드 사용 (ack)
     *
     * @Retryable:
     * - DB Deadlock/Lock 실패 시 자동 재시도
     * - maxAttempts: 5회
     * - backoff: 100ms → 150ms → 225ms → 337ms → 500ms
     *
     * @Transactional(propagation = REQUIRES_NEW):
     * - 새로운 트랜잭션 시작 (독립적)
     * - 재시도 시마다 새로운 트랜잭션 생성
     *
     * @param event 쿠폰 발급 이벤트
     * @param partition 파티션 번호
     * @param offset 오프셋
     * @param ack 수동 커밋 객체
     */
    @KafkaListener(
        topics = "coupon-events",
        groupId = "coupon-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
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
    public void handleCouponIssued(
            @Payload CouponIssuedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("[Kafka Consumer] 쿠폰 발급 DB 동기화 시작 - partition: {}, offset: {}, event: {}",
                 partition, offset, event);

        try {
            // 1. 엔티티 조회
            Coupon coupon = couponRepository.findById(event.getCouponId())
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다. ID: " + event.getCouponId()));

            User user = userRepository.findById(event.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + event.getUserId()));

            // 2. 중복 발급 체크 (멱등성 보장)
            Long issuedCount = userCouponRepository.countByUserAndCoupon(user, coupon);
            if (issuedCount > 0) {
                log.warn("[Kafka Consumer] 이미 발급된 쿠폰 - userId: {}, couponId: {}, 발급 횟수: {}, partition: {}, offset: {}",
                         event.getUserId(), event.getCouponId(), issuedCount, partition, offset);
                ack.acknowledge(); // 중복은 성공으로 간주하고 커밋
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

            // 5. 수동 커밋
            ack.acknowledge();

            log.info("[Kafka Consumer] 쿠폰 발급 DB 동기화 완료 - userId: {}, couponId: {}, userCouponId: {}, rank: {}, issuedCount: {}, partition: {}, offset: {}",
                     event.getUserId(), event.getCouponId(), savedUserCoupon.getId(), event.getRank(), event.getIssuedCount(), partition, offset);

        } catch (DeadlockLoserDataAccessException | CannotAcquireLockException e) {
            log.warn("[Kafka Consumer] DB Lock 실패, 재시도 예정 - userId: {}, couponId: {}, partition: {}, offset: {}, error: {}",
                     event.getUserId(), event.getCouponId(), partition, offset, e.getMessage());
            throw e; // @Retryable이 재시도

        } catch (DataIntegrityViolationException e) {
            log.warn("[Kafka Consumer] DB 제약조건 위반 (중복 발급 가능성) - userId: {}, couponId: {}, partition: {}, offset: {}, error: {}",
                     event.getUserId(), event.getCouponId(), partition, offset, e.getMessage());
            ack.acknowledge(); // 중복은 성공으로 간주하고 커밋

        } catch (Exception e) {
            log.error("[Kafka Consumer] 쿠폰 발급 DB 동기화 실패 - userId: {}, couponId: {}, partition: {}, offset: {}, error: {}",
                      event.getUserId(), event.getCouponId(), partition, offset, e.getMessage(), e);
            throw e; // 재시도 또는 DLQ로 전송
        }
    }
}

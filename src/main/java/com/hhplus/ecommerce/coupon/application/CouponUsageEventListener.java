package com.hhplus.ecommerce.coupon.application;

import com.hhplus.ecommerce.common.application.DomainEventStoreService;
import com.hhplus.ecommerce.common.domain.DomainEventStore;
import com.hhplus.ecommerce.common.domain.event.CouponUsagePayload;
import com.hhplus.ecommerce.coupon.domain.UserCoupon;
import com.hhplus.ecommerce.coupon.domain.UserCouponStatus;
import com.hhplus.ecommerce.coupon.infrastructure.persistence.UserCouponRepository;
import com.hhplus.ecommerce.order.domain.Order;
import com.hhplus.ecommerce.order.domain.event.OrderCompletedEvent;
import com.hhplus.ecommerce.order.infrastructure.persistence.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 쿠폰 사용 이벤트 리스너
 *
 * Application Layer - 이벤트 핸들러
 *
 * 책임:
 * - 주문 완료 후 쿠폰 사용 처리
 * - TransactionalEventListener의 AFTER_COMMIT으로 실행
 *
 * 실행 시점:
 * - 주문 트랜잭션이 성공적으로 커밋된 후
 * - OrderCompletedEvent 발행 시
 *
 * 처리 내용:
 * 1. 쿠폰 사용 처리 (markAsUsed)
 * 2. 주문에 쿠폰 적용 기록 (applyCoupon)
 *
 * 트랜잭션:
 * - REQUIRES_NEW: 독립적인 새 트랜잭션 생성
 * - 주문 트랜잭션과 분리되어 실행
 *
 * 장점:
 * - 주문 트랜잭션과 쿠폰 처리 분리
 * - 쿠폰 처리 실패 시 주문은 성공 유지
 * - 비동기 처리 가능 (향후 확장)
 *
 * 주의사항:
 * - AFTER_COMMIT이므로 실패 시 주문 롤백 불가
 * - 실패 시 보상 트랜잭션 또는 재시도 필요
 * - 멱등성 보장 필요 (중복 처리 방지)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponUsageEventListener {

    private final UserCouponRepository userCouponRepository;
    private final OrderRepository orderRepository;
    private final DomainEventStoreService eventStoreService;

    /**
     * 주문 완료 후 쿠폰 사용 처리
     *
     * 실행 조건:
     * - 주문 트랜잭션 커밋 성공
     * - 쿠폰이 사용된 주문인 경우
     *
     * 처리 내용:
     * 1. UserCoupon 조회
     * 2. 쿠폰 사용 처리 (markAsUsed)
     * 3. Order 조회
     * 4. 주문에 쿠폰 적용 기록 (applyCoupon)
     *
     * 예외 처리:
     * - 쿠폰 또는 주문을 찾을 수 없는 경우: 로그만 기록 (이미 처리된 경우)
     * - 쿠폰이 이미 사용된 경우: 멱등성 보장 (중복 처리 방지)
     * - 기타 예외: 로그 기록 및 재시도 필요
     *
     * @param event 주문 완료 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        // 쿠폰 사용이 없는 경우 스킵
        if (!event.hasCoupon()) {
            log.debug("[이벤트] 쿠폰 사용 없음 - orderId: {}", event.getOrderId());
            return;
        }

        log.info("[이벤트] 쿠폰 사용 처리 시작 - orderId: {}, userCouponId: {}",
                 event.getOrderId(), event.getUserCouponId());

        try {
            // Step 1: UserCoupon 조회
            UserCoupon userCoupon = userCouponRepository.findById(event.getUserCouponId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "사용자 쿠폰을 찾을 수 없습니다. userCouponId: " + event.getUserCouponId()));

            // Step 2: 쿠폰 사용 처리 (멱등성 체크 포함)
            if (userCoupon.getStatus() == UserCouponStatus.USED) {
                log.warn("[이벤트] 쿠폰이 이미 사용됨 - userCouponId: {}, orderId: {}",
                         event.getUserCouponId(), event.getOrderId());
                // 이미 사용된 쿠폰이면 스킵 (멱등성 보장)
                return;
            }

            userCoupon.markAsUsed();
            userCouponRepository.save(userCoupon);

            // Step 3: Order 조회
            Order order = orderRepository.findById(event.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "주문을 찾을 수 없습니다. orderId: " + event.getOrderId()));

            // Step 4: 주문에 쿠폰 적용 기록
            order.applyCoupon(userCoupon, event.getDiscountAmount());
            orderRepository.save(order);

            log.info("[이벤트] 쿠폰 사용 처리 완료 - orderId: {}, userCouponId: {}, discountAmount: {}",
                     event.getOrderId(), event.getUserCouponId(), event.getDiscountAmount());

        } catch (IllegalArgumentException e) {
            // 데이터를 찾을 수 없는 경우 (이미 처리됨)
            log.error("[이벤트] 쿠폰 사용 처리 실패 (데이터 없음) - orderId: {}, error: {}",
                      event.getOrderId(), e.getMessage());

            // 보상 트랜잭션: 이벤트 소싱을 통한 실패 이벤트 저장
            saveToDomainEventStore(event);

        } catch (Exception e) {
            // 기타 예외: 로그 기록 및 보상 트랜잭션
            log.error("[이벤트] 쿠폰 사용 처리 중 예외 발생 - orderId: {}, userCouponId: {}",
                      event.getOrderId(), event.getUserCouponId(), e);

            // 보상 트랜잭션: 이벤트 소싱을 통한 실패 이벤트 저장
            saveToDomainEventStore(event);

            // 주문은 이미 완료되었으므로 예외를 던지지 않음
            // 대신 보상 트랜잭션으로 실패 이벤트 저장 후 재시도 메커니즘 동작
        }
    }

    /**
     * 도메인 이벤트 스토어에 실패 이벤트 저장 (보상 트랜잭션)
     *
     * 이벤트 소싱 패턴:
     * - DomainEventStoreService가 REQUIRES_NEW로 독립적인 트랜잭션 실행
     * - 이벤트 저장 실패 시에도 로그만 기록
     * - 자동 재시도 메커니즘이 나중에 처리
     *
     * @param event 주문 완료 이벤트
     */
    private void saveToDomainEventStore(OrderCompletedEvent event) {
        // 쿠폰 사용 페이로드 생성
        CouponUsagePayload payload = CouponUsagePayload.builder()
            .orderId(event.getOrderId())
            .userCouponId(event.getUserCouponId())
            .userId(event.getUserId())
            .discountAmount(event.getDiscountAmount())
            .build();

        // 이벤트 스토어에 저장 (이벤트 소싱)
        eventStoreService.saveEvent(
            DomainEventStore.EventType.COUPON_USAGE,
            event.getOrderId(),  // Aggregate ID: Order ID
            "Order",             // Aggregate Type
            payload
        );
    }
}

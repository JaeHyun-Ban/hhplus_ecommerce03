package com.hhplus.ecommerce.product.application;

import com.hhplus.ecommerce.order.domain.event.OrderCreatedEvent;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 잔액 차감 이벤트
 *
 * Application Layer - 애플리케이션 이벤트
 *
 * 책임:
 * - 재고 차감 성공 후 발행되는 이벤트
 * - 잔액 차감 처리를 위한 정보 전달
 * - 결제 완료 처리를 위한 정보 전달
 *
 * 이벤트 발행 시점:
 * - 재고 차감 성공 후
 *
 * 이벤트 처리 시점:
 * - TransactionalEventListener의 AFTER_COMMIT
 * - 재고 차감 트랜잭션이 성공적으로 커밋된 후
 *
 * 처리 내용:
 * - 잔액 차감 (BalanceDeductionEventListener)
 * - 결제 완료 (PaymentEventListener)
 * - 쿠폰 사용 (CouponUsageEventListener)
 * - 인기상품 집계 (PopularProductEventListener)
 *
 * 주의사항:
 * - AFTER_COMMIT이므로 실패 시 보상 트랜잭션 필요
 * - Saga 패턴의 두 번째 단계
 */
@Getter
@Builder
public class BalanceDeductionEvent {

    /**
     * 주문 ID
     */
    private final Long orderId;

    /**
     * 주문 번호
     */
    private final String orderNumber;

    /**
     * 사용자 ID
     */
    private final Long userId;

    /**
     * 차감할 금액
     */
    private final BigDecimal amount;

    /**
     * 사용자 쿠폰 ID (선택)
     */
    private final Long userCouponId;

    /**
     * 할인 금액
     */
    private final BigDecimal discountAmount;

    /**
     * 주문 상품 정보 (인기상품 집계용)
     */
    private final List<OrderCreatedEvent.OrderProductInfo> orderProducts;

    /**
     * 쿠폰 사용 여부
     */
    public boolean hasCoupon() {
        return userCouponId != null;
    }
}
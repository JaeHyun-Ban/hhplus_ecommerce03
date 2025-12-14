package com.hhplus.ecommerce.order.domain.event;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 주문 생성 이벤트
 *
 * Domain Layer - 도메인 이벤트
 *
 * 책임:
 * - 주문 생성 시 발행되는 이벤트
 * - 재고 차감 처리를 위한 정보 전달
 * - 잔액 차감 처리를 위한 정보 전달
 * - 결제 처리를 위한 정보 전달
 *
 * 이벤트 발행 시점:
 * - 주문 엔티티 생성 직후 (트랜잭션 커밋 전)
 *
 * 이벤트 처리 시점:
 * - TransactionalEventListener의 AFTER_COMMIT
 * - 주문 트랜잭션이 성공적으로 커밋된 후
 *
 * 처리 내용:
 * - 재고 차감 (StockDeductionEventListener)
 * - 잔액 차감 (BalanceDeductionEventListener)
 * - 결제 생성 및 완료 (PaymentEventListener)
 *
 * 주의사항:
 * - AFTER_COMMIT이므로 실패 시 보상 트랜잭션 필요
 * - Saga 패턴으로 분산 트랜잭션 구현
 */
@Getter
@Builder
public class OrderCreatedEvent {

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
     * 최종 결제 금액
     */
    private final BigDecimal finalAmount;

    /**
     * 주문 상품 정보 (재고 차감용)
     */
    private final List<OrderProductInfo> orderProducts;

    /**
     * 사용자 쿠폰 ID (선택)
     */
    private final Long userCouponId;

    /**
     * 할인 금액
     */
    private final BigDecimal discountAmount;

    /**
     * 쿠폰 사용 여부
     */
    public boolean hasCoupon() {
        return userCouponId != null;
    }

    /**
     * 주문 상품 정보 DTO
     */
    @Getter
    @Builder
    public static class OrderProductInfo {
        private final Long productId;
        private final Integer quantity;
        private final BigDecimal price;
    }
}
package com.hhplus.ecommerce.order.domain.event;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 주문 완료 이벤트
 *
 * Domain Layer - 도메인 이벤트
 *
 * 책임:
 * - 주문 완료 시 발행되는 이벤트
 * - 쿠폰 사용 처리를 위한 정보 전달
 * - 인기상품 집계를 위한 정보 전달
 *
 * 이벤트 발행 시점:
 * - 주문 트랜잭션 커밋 직전
 *
 * 이벤트 처리 시점:
 * - TransactionalEventListener의 AFTER_COMMIT
 * - 주문 트랜잭션이 성공적으로 커밋된 후
 *
 * 처리 내용:
 * - 쿠폰 사용 처리 (CouponUsageEventListener)
 * - 인기상품 집계 (PopularProductEventListener)
 *
 * 주의사항:
 * - AFTER_COMMIT이므로 실패 시 롤백 불가
 * - 실패 시 보상 트랜잭션 필요
 */
@Getter
@Builder
public class OrderCompletedEvent {

    /**
     * 주문 ID
     */
    private final Long orderId;

    /**
     * 사용자 쿠폰 ID (쿠폰 사용 시에만 존재)
     */
    private final Long userCouponId;

    /**
     * 할인 금액
     */
    private final BigDecimal discountAmount;

    /**
     * 사용자 ID
     */
    private final Long userId;

    /**
     * 주문 상품 정보 (인기상품 집계용)
     */
    private final List<OrderProductInfo> orderProducts;

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
    }
}

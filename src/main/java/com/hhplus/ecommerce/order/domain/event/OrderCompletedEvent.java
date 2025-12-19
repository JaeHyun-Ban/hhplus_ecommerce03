package com.hhplus.ecommerce.order.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 주문 완료 이벤트 (Kafka 기반)
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
 * - Kafka Consumer에서 메시지 수신 후
 * - 주문 트랜잭션이 성공적으로 커밋된 후
 *
 * 처리 내용:
 * - 쿠폰 사용 처리 (OrderCompletedKafkaConsumer)
 * - 인기상품 집계 (OrderCompletedKafkaConsumer)
 *
 * 주의사항:
 * - Kafka를 통한 비동기 처리
 * - 실패 시 보상 트랜잭션 및 재시도 필요
 */
@Getter
@NoArgsConstructor  // Kafka JSON 역직렬화용
@AllArgsConstructor
@Builder
public class OrderCompletedEvent {

    /**
     * 주문 ID
     */
    private Long orderId;

    /**
     * 사용자 쿠폰 ID (쿠폰 사용 시에만 존재)
     */
    private Long userCouponId;

    /**
     * 할인 금액
     */
    private BigDecimal discountAmount;

    /**
     * 사용자 ID
     */
    private Long userId;

    /**
     * 주문 상품 정보 (인기상품 집계용)
     */
    private List<OrderProductInfo> orderProducts;

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
    @NoArgsConstructor  // Kafka JSON 역직렬화용
    @AllArgsConstructor
    @Builder
    public static class OrderProductInfo {
        private Long productId;
        private Integer quantity;
    }
}

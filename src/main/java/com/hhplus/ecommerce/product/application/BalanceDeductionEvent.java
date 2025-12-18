package com.hhplus.ecommerce.product.application;

import com.hhplus.ecommerce.order.domain.event.OrderCreatedEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 잔액 차감 이벤트 (Kafka 기반)
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
 * - Kafka Consumer에서 메시지 수신 후
 * - 재고 차감 트랜잭션이 성공적으로 커밋된 후
 *
 * 처리 내용:
 * - 잔액 차감 (PaymentKafkaConsumer)
 * - 결제 완료 (PaymentKafkaConsumer)
 * - 쿠폰 사용 (OrderCompletedKafkaConsumer)
 * - 인기상품 집계 (OrderCompletedKafkaConsumer)
 *
 * 주의사항:
 * - Kafka를 통한 비동기 처리
 * - 실패 시 보상 트랜잭션 및 재시도 필요
 * - Saga 패턴의 두 번째 단계
 */
@Getter
@NoArgsConstructor  // Kafka JSON 역직렬화용
@AllArgsConstructor
@Builder
public class BalanceDeductionEvent {

    /**
     * 주문 ID
     */
    private Long orderId;

    /**
     * 주문 번호
     */
    private String orderNumber;

    /**
     * 사용자 ID
     */
    private Long userId;

    /**
     * 차감할 금액
     */
    private BigDecimal amount;

    /**
     * 사용자 쿠폰 ID (선택)
     */
    private Long userCouponId;

    /**
     * 할인 금액
     */
    private BigDecimal discountAmount;

    /**
     * 주문 상품 정보 (인기상품 집계용)
     */
    private List<OrderCreatedEvent.OrderProductInfo> orderProducts;

    /**
     * 쿠폰 사용 여부
     */
    public boolean hasCoupon() {
        return userCouponId != null;
    }
}
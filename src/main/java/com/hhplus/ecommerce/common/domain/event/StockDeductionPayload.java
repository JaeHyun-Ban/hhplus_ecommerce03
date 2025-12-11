package com.hhplus.ecommerce.common.domain.event;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 재고 차감 이벤트 페이로드
 *
 * Common Layer - 이벤트 페이로드
 *
 * 책임:
 * - 재고 차감 이벤트 데이터 저장
 * - 이벤트 소싱을 위한 JSON 직렬화/역직렬화
 */
@Getter
@NoArgsConstructor
public class StockDeductionPayload implements EventPayload {

    private Long orderId;
    private String orderNumber;
    private Long userId;
    private List<OrderProductInfo> orderProducts;
    private String failureReason;

    @Builder
    public StockDeductionPayload(
        Long orderId,
        String orderNumber,
        Long userId,
        List<OrderProductInfo> orderProducts,
        String failureReason
    ) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.userId = userId;
        this.orderProducts = orderProducts;
        this.failureReason = failureReason;
    }

    @Getter
    @NoArgsConstructor
    public static class OrderProductInfo {
        private Long productId;
        private Integer quantity;

        @Builder
        public OrderProductInfo(Long productId, Integer quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }
    }
}

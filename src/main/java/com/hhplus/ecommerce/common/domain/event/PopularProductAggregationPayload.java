package com.hhplus.ecommerce.common.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 인기상품 집계 이벤트 페이로드
 *
 * Common Layer - 이벤트 페이로드
 *
 * 책임:
 * - 인기상품 집계 처리에 필요한 모든 데이터 보관
 * - JSON 직렬화/역직렬화 지원
 */
@Getter
@Builder
public class PopularProductAggregationPayload implements EventPayload {

    /**
     * 주문 ID
     */
    private final Long orderId;

    /**
     * 사용자 ID
     */
    private final Long userId;

    /**
     * 주문 상품 목록
     */
    private final List<OrderProductInfo> orderProducts;

    /**
     * Jackson 역직렬화를 위한 생성자
     */
    @JsonCreator
    public PopularProductAggregationPayload(
        @JsonProperty("orderId") Long orderId,
        @JsonProperty("userId") Long userId,
        @JsonProperty("orderProducts") List<OrderProductInfo> orderProducts
    ) {
        this.orderId = orderId;
        this.userId = userId;
        this.orderProducts = orderProducts;
    }

    /**
     * 주문 상품 정보 DTO
     */
    @Getter
    @Builder
    public static class OrderProductInfo {
        private final Long productId;
        private final Integer quantity;

        @JsonCreator
        public OrderProductInfo(
            @JsonProperty("productId") Long productId,
            @JsonProperty("quantity") Integer quantity
        ) {
            this.productId = productId;
            this.quantity = quantity;
        }
    }
}

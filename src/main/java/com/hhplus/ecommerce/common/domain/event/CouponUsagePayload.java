package com.hhplus.ecommerce.common.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 쿠폰 사용 이벤트 페이로드
 *
 * Common Layer - 이벤트 페이로드
 *
 * 책임:
 * - 쿠폰 사용 처리에 필요한 모든 데이터 보관
 * - JSON 직렬화/역직렬화 지원
 */
@Getter
@Builder
public class CouponUsagePayload implements EventPayload {

    /**
     * 주문 ID
     */
    private final Long orderId;

    /**
     * 사용자 쿠폰 ID
     */
    private final Long userCouponId;

    /**
     * 사용자 ID
     */
    private final Long userId;

    /**
     * 할인 금액
     */
    private final BigDecimal discountAmount;

    /**
     * Jackson 역직렬화를 위한 생성자
     */
    @JsonCreator
    public CouponUsagePayload(
        @JsonProperty("orderId") Long orderId,
        @JsonProperty("userCouponId") Long userCouponId,
        @JsonProperty("userId") Long userId,
        @JsonProperty("discountAmount") BigDecimal discountAmount
    ) {
        this.orderId = orderId;
        this.userCouponId = userCouponId;
        this.userId = userId;
        this.discountAmount = discountAmount;
    }
}

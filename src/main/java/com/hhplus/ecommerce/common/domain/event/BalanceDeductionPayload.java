package com.hhplus.ecommerce.common.domain.event;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 잔액 차감 이벤트 페이로드
 *
 * Common Layer - 이벤트 페이로드
 *
 * 책임:
 * - 잔액 차감 이벤트 데이터 저장
 * - 이벤트 소싱을 위한 JSON 직렬화/역직렬화
 */
@Getter
@NoArgsConstructor
public class BalanceDeductionPayload implements EventPayload {

    private Long orderId;
    private String orderNumber;
    private Long userId;
    private BigDecimal amount;
    private String failureReason;

    @Builder
    public BalanceDeductionPayload(
        Long orderId,
        String orderNumber,
        Long userId,
        BigDecimal amount,
        String failureReason
    ) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.userId = userId;
        this.amount = amount;
        this.failureReason = failureReason;
    }
}

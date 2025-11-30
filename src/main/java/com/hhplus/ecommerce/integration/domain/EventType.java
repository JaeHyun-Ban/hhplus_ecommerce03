package com.hhplus.ecommerce.integration.domain;

public enum EventType {
    ORDER_CREATED,     // 주문 생성
    ORDER_CANCELLED,   // 주문 취소
    ORDER_PAID,        // 주문 결제 완료
    ORDER_REFUNDED     // 주문 환불
}

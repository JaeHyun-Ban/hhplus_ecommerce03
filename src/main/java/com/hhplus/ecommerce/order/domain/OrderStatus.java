package com.hhplus.ecommerce.order.domain;

public enum OrderStatus {
    PENDING,    // 결제 대기
    PAID,       // 결제 완료
    CANCELLED,  // 취소
    REFUNDED    // 환불
}

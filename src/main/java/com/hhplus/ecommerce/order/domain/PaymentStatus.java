package com.hhplus.ecommerce.order.domain;

public enum PaymentStatus {
    PENDING,    // 대기 중
    COMPLETED,  // 완료
    FAILED,     // 실패
    CANCELLED   // 취소
}

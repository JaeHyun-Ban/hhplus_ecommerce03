package com.hhplus.ecommerce.integration.domain;

public enum EventStatus {
    PENDING,      // 대기 중
    SENDING,      // 전송 중
    SUCCESS,      // 성공
    FAILED,       // 실패
    DEAD_LETTER   // 최종 실패 (DLQ)
}

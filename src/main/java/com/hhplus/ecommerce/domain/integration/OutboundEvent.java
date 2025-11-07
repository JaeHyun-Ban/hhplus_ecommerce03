package com.hhplus.ecommerce.domain.integration;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbound_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OutboundEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventType eventType;

    @Column(nullable = false)
    private Long entityId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @Column(nullable = false)
    private Integer retryCount;

    @Column(nullable = false)
    private Integer maxRetryCount;

    private LocalDateTime nextRetryAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = EventStatus.PENDING;
        }
        if (this.retryCount == null) {
            this.retryCount = 0;
        }
        if (this.maxRetryCount == null) {
            this.maxRetryCount = 3;
        }
    }

    // 비즈니스 로직: 전송 시작
    public void startSending() {
        if (this.status != EventStatus.PENDING && this.status != EventStatus.FAILED) {
            throw new IllegalStateException("대기 중이거나 실패한 이벤트만 전송할 수 있습니다.");
        }
        this.status = EventStatus.SENDING;
        this.sentAt = LocalDateTime.now();
    }

    // 비즈니스 로직: 전송 성공
    public void markAsSuccess() {
        if (this.status != EventStatus.SENDING) {
            throw new IllegalStateException("전송 중인 이벤트만 성공 처리할 수 있습니다.");
        }
        this.status = EventStatus.SUCCESS;
        this.completedAt = LocalDateTime.now();
    }

    // 비즈니스 로직: 전송 실패 및 재시도 예약
    public void markAsFailedAndScheduleRetry(String errorMessage) {
        this.retryCount++;
        this.errorMessage = errorMessage;

        if (this.retryCount >= this.maxRetryCount) {
            // 최대 재시도 횟수 초과 시 Dead Letter Queue로 이동
            this.status = EventStatus.DEAD_LETTER;
            this.completedAt = LocalDateTime.now();
        } else {
            // 재시도 예약 (지수 백오프: 2^retryCount 분 후)
            this.status = EventStatus.FAILED;
            int delayMinutes = (int) Math.pow(2, this.retryCount);
            this.nextRetryAt = LocalDateTime.now().plusMinutes(delayMinutes);
        }
    }

    // 비즈니스 로직: 재시도 가능 여부 확인
    public boolean canRetry() {
        return this.status == EventStatus.FAILED
                && this.nextRetryAt != null
                && LocalDateTime.now().isAfter(this.nextRetryAt);
    }

    // 비즈니스 로직: 수동 재시도
    public void resetForManualRetry() {
        if (this.status != EventStatus.DEAD_LETTER && this.status != EventStatus.FAILED) {
            throw new IllegalStateException("실패하거나 DLQ에 있는 이벤트만 수동 재시도할 수 있습니다.");
        }
        this.status = EventStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = null;
        this.errorMessage = null;
    }
}

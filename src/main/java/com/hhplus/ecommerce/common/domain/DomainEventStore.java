package com.hhplus.ecommerce.common.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 도메인 이벤트 스토어
 *
 * Common Layer - 범용 도메인 이벤트 저장소
 *
 * 책임:
 * - 모든 도메인 이벤트 저장 (이벤트 소싱)
 * - 이벤트 처리 실패 시 재시도 메커니즘
 * - 도메인 상태 변경 히스토리 추적
 *
 * 이벤트 타입:
 * - COUPON_USAGE: 쿠폰 사용 처리
 * - POPULAR_PRODUCT_AGGREGATION: 인기상품 집계
 * - ORDER_COMPLETED: 주문 완료
 * - 기타 도메인 이벤트...
 *
 * 이벤트 상태:
 * - PENDING: 처리 대기 중
 * - PROCESSING: 처리 중
 * - COMPLETED: 처리 완료
 * - FAILED: 최종 실패 (수동 처리 필요)
 *
 * 재시도 전략:
 * - Exponential Backoff (1분 → 5분 → 15분)
 * - 최대 3회 재시도
 *
 * Use Cases:
 * - 모든 도메인 이벤트의 이벤트 소싱
 * - 이벤트 처리 실패 시 보상 트랜잭션
 * - 도메인 상태 변경 감사(Audit) 로그
 */
@Entity
@Table(
    name = "domain_event_store",
    indexes = {
        @Index(name = "idx_event_type_status", columnList = "event_type, status"),
        @Index(name = "idx_aggregate_id", columnList = "aggregate_id"),
        @Index(name = "idx_status_next_retry", columnList = "status, next_retry_at"),
        @Index(name = "idx_created_at", columnList = "created_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DomainEventStore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 이벤트 타입
     * - COUPON_USAGE, POPULAR_PRODUCT_AGGREGATION 등
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EventType eventType;

    /**
     * 이벤트 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status = EventStatus.PENDING;

    /**
     * Aggregate ID (연관된 도메인 엔티티의 ID)
     * - 예: orderId, userId, couponId 등
     */
    @Column(nullable = false)
    private Long aggregateId;

    /**
     * Aggregate Type (연관된 도메인 엔티티의 타입)
     * - 예: Order, User, Coupon 등
     */
    @Column(nullable = false, length = 50)
    private String aggregateType;

    /**
     * 이벤트 페이로드 (JSON)
     * - 이벤트에 필요한 모든 데이터를 JSON으로 저장
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    /**
     * 실패 사유
     */
    @Column(length = 2000)
    private String failureReason;

    /**
     * 재시도 횟수
     */
    @Column(nullable = false)
    private Integer retryCount = 0;

    /**
     * 최대 재시도 횟수
     */
    @Column(nullable = false)
    private Integer maxRetryCount = 3;

    /**
     * 다음 재시도 시각
     */
    @Column
    private LocalDateTime nextRetryAt;

    /**
     * 처리 완료 시각
     */
    @Column
    private LocalDateTime completedAt;

    /**
     * 생성 시각 (이벤트 발생 시각)
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 수정 시각
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 이벤트 타입
     */
    public enum EventType {
        COUPON_USAGE,                   // 쿠폰 사용 처리
        POPULAR_PRODUCT_AGGREGATION,    // 인기상품 집계
        ORDER_COMPLETED,                // 주문 완료
        PAYMENT_COMPLETED,              // 결제 완료
        BALANCE_CHARGED,                // 잔액 충전
        PRODUCT_STOCK_DECREASED         // 상품 재고 감소
        // 필요에 따라 추가 가능
    }

    /**
     * 이벤트 상태
     */
    public enum EventStatus {
        PENDING,        // 처리 대기 중
        PROCESSING,     // 처리 중
        COMPLETED,      // 처리 완료
        FAILED          // 최종 실패 (수동 처리 필요)
    }

    /**
     * JPA 생명주기 콜백 - 엔티티 생성 시
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * JPA 생명주기 콜백 - 엔티티 수정 시
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public DomainEventStore(EventType eventType, Long aggregateId, String aggregateType, String payload) {
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.payload = payload;
        this.status = EventStatus.PENDING;
        this.retryCount = 0;
        this.maxRetryCount = 3;
        this.nextRetryAt = calculateNextRetryAt(0);
    }

    /**
     * 처리 시작
     */
    public void startProcessing() {
        if (this.status == EventStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 이벤트는 처리할 수 없습니다.");
        }
        if (this.status == EventStatus.FAILED) {
            throw new IllegalStateException("최종 실패한 이벤트는 처리할 수 없습니다. 수동 처리가 필요합니다.");
        }
        if (this.retryCount >= this.maxRetryCount) {
            throw new IllegalStateException("최대 재시도 횟수를 초과했습니다.");
        }

        this.status = EventStatus.PROCESSING;
    }

    /**
     * 처리 성공
     */
    public void markAsCompleted() {
        this.status = EventStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 처리 실패
     */
    public void markAsFailed(String reason) {
        this.retryCount++;

        // 실패 사유 누적 (히스토리 추적)
        if (this.failureReason == null) {
            this.failureReason = "[시도 " + this.retryCount + "] " + reason;
        } else {
            this.failureReason = this.failureReason + "\n[시도 " + this.retryCount + "] " + reason;
        }

        if (this.retryCount >= this.maxRetryCount) {
            // 최대 재시도 횟수 초과 - 최종 실패
            this.status = EventStatus.FAILED;
        } else {
            // 다음 재시도 대기
            this.status = EventStatus.PENDING;
            this.nextRetryAt = calculateNextRetryAt(this.retryCount);
        }
    }

    /**
     * 다음 재시도 시각 계산 (Exponential Backoff)
     *
     * 재시도 간격:
     * - 1회: 1분 후
     * - 2회: 5분 후
     * - 3회: 15분 후
     */
    private LocalDateTime calculateNextRetryAt(int retryCount) {
        int delayMinutes = switch (retryCount) {
            case 0 -> 1;    // 첫 재시도: 1분 후
            case 1 -> 5;    // 두 번째 재시도: 5분 후
            case 2 -> 15;   // 세 번째 재시도: 15분 후
            default -> 30;  // 그 이후: 30분 후
        };

        return LocalDateTime.now().plusMinutes(delayMinutes);
    }

    /**
     * 재시도 가능 여부 확인
     */
    public boolean canRetry() {
        return this.status == EventStatus.PENDING
            && this.retryCount < this.maxRetryCount
            && this.nextRetryAt != null
            && LocalDateTime.now().isAfter(this.nextRetryAt);
    }

    /**
     * 수동 재시도를 위한 상태 초기화
     * (관리자가 수동으로 재시도할 때 사용)
     */
    public void resetForManualRetry() {
        if (this.status != EventStatus.FAILED) {
            throw new IllegalStateException("최종 실패 상태인 이벤트만 수동 재시도할 수 있습니다.");
        }
        this.status = EventStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = LocalDateTime.now();
        this.failureReason = this.failureReason + "\n[수동 재시도] 관리자에 의한 재시도";
    }
}

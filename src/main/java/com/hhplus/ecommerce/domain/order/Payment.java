package com.hhplus.ecommerce.domain.order;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_order_id", columnList = "order_id"),
        @Index(name = "idx_status_method", columnList = "status, method")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    @Column(length = 500)
    private String failureReason;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = PaymentStatus.PENDING;
        }
    }

    // 비즈니스 로직: 결제 완료
    public void complete() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("대기 중인 결제만 완료할 수 있습니다.");
        }
        this.status = PaymentStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    // 비즈니스 로직: 결제 실패
    public void fail(String reason) {
        if (this.status == PaymentStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 결제는 실패 처리할 수 없습니다.");
        }
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    // 비즈니스 로직: 결제 취소
    public void cancel() {
        if (this.status != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("완료된 결제만 취소할 수 있습니다.");
        }
        this.status = PaymentStatus.CANCELLED;
    }
}

package com.hhplus.ecommerce.domain.product;

import com.hhplus.ecommerce.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "restock_notifications",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id", "status"}),
        indexes = {
                @Index(name = "idx_product_status", columnList = "product_id, status"),
                @Index(name = "idx_user_status", columnList = "user_id, status")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RestockNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        if (this.requestedAt == null) {
            this.requestedAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = NotificationStatus.PENDING;
        }
    }

    // 비즈니스 로직: 알림 발송 완료
    public void markAsSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    // 비즈니스 로직: 알림 취소
    public void cancel() {
        this.status = NotificationStatus.CANCELLED;
    }
}

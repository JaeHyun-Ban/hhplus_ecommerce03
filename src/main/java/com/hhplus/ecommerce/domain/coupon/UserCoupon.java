package com.hhplus.ecommerce.domain.coupon;

import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserCouponStatus status;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    private LocalDateTime usedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order usedOrder;

    private LocalDateTime expiredAt;

    @Column(length = 500)
    private String revocationReason;

    @PrePersist
    protected void onCreate() {
        if (this.issuedAt == null) {
            this.issuedAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = UserCouponStatus.ISSUED;
        }
    }

    // 비즈니스 로직: 쿠폰 사용
    public void use(Order order) {
        if (this.status != UserCouponStatus.ISSUED) {
            throw new IllegalStateException("발급된 쿠폰만 사용할 수 있습니다.");
        }
        if (!this.coupon.isValid()) {
            throw new IllegalStateException("유효하지 않은 쿠폰입니다.");
        }
        this.status = UserCouponStatus.USED;
        this.usedAt = LocalDateTime.now();
        this.usedOrder = order;
    }

    // 비즈니스 로직: 쿠폰 만료
    public void expire() {
        if (this.status != UserCouponStatus.ISSUED) {
            throw new IllegalStateException("발급된 쿠폰만 만료 처리할 수 있습니다.");
        }
        this.status = UserCouponStatus.EXPIRED;
        this.expiredAt = LocalDateTime.now();
    }

    // 비즈니스 로직: 쿠폰 회수
    public void revoke(String reason) {
        if (this.status == UserCouponStatus.USED) {
            throw new IllegalStateException("이미 사용된 쿠폰은 회수할 수 없습니다.");
        }
        this.status = UserCouponStatus.REVOKED;
        this.revocationReason = reason;
    }

    // 비즈니스 로직: 쿠폰 복구 (주문 취소 시)
    public void restore() {
        if (this.status != UserCouponStatus.USED) {
            throw new IllegalStateException("사용된 쿠폰만 복구할 수 있습니다.");
        }
        // 쿠폰 유효기간이 지나지 않았다면 복구
        if (this.coupon.isValid()) {
            this.status = UserCouponStatus.ISSUED;
            this.usedAt = null;
            this.usedOrder = null;
        } else {
            this.status = UserCouponStatus.EXPIRED;
            this.expiredAt = LocalDateTime.now();
        }
    }

    // 비즈니스 로직: 사용 가능 여부 확인
    public boolean canUse() {
        return this.status == UserCouponStatus.ISSUED && this.coupon.isValid();
    }

    // 비즈니스 로직: 쿠폰 사용 표시 (별칭 메서드 - use()와 동일)
    public void markAsUsed() {
        if (this.status != UserCouponStatus.ISSUED) {
            throw new IllegalStateException("발급된 쿠폰만 사용할 수 있습니다.");
        }
        if (!this.coupon.isValid()) {
            throw new IllegalStateException("유효하지 않은 쿠폰입니다.");
        }
        this.status = UserCouponStatus.USED;
        this.usedAt = LocalDateTime.now();
    }
}

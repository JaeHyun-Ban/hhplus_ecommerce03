package com.hhplus.ecommerce.domain.coupon;

import com.hhplus.ecommerce.domain.common.BaseEntity;
import com.hhplus.ecommerce.domain.product.Category;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Coupon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponType type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal discountValue;

    @Column(precision = 15, scale = 2)
    private BigDecimal minimumOrderAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal maximumDiscountAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category applicableCategory;

    @Column(nullable = false)
    private Integer totalQuantity;

    @Column(nullable = false)
    private Integer issuedQuantity;

    @Column(nullable = false)
    private Integer maxIssuePerUser;

    @Column(nullable = false)
    private LocalDateTime issueStartAt;

    @Column(nullable = false)
    private LocalDateTime issueEndAt;

    @Column(nullable = false)
    private LocalDateTime validFrom;

    @Column(nullable = false)
    private LocalDateTime validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponStatus status;

    @Version
    private Long version; // 낙관적 락

    // 비즈니스 로직: 쿠폰 발급 가능 여부 확인
    public boolean canIssue() {
        LocalDateTime now = LocalDateTime.now();
        return this.status == CouponStatus.ACTIVE
                && this.issuedQuantity < this.totalQuantity
                && now.isAfter(this.issueStartAt)
                && now.isBefore(this.issueEndAt);
    }

    // 비즈니스 로직: 쿠폰 발급
    public void issue() {
        if (!canIssue()) {
            throw new IllegalStateException("쿠폰을 발급할 수 없습니다.");
        }
        this.issuedQuantity++;
        if (this.issuedQuantity >= this.totalQuantity) {
            this.status = CouponStatus.EXHAUSTED;
        }
    }

    // 비즈니스 로직: 할인 금액 계산
    public BigDecimal calculateDiscountAmount(BigDecimal orderAmount) {
        // 최소 주문 금액 확인
        if (this.minimumOrderAmount != null && orderAmount.compareTo(this.minimumOrderAmount) < 0) {
            throw new IllegalArgumentException("최소 주문 금액을 충족하지 못했습니다.");
        }

        BigDecimal discountAmount;
        if (this.type == CouponType.FIXED_AMOUNT) {
            // 정액 할인
            discountAmount = this.discountValue;
        } else {
            // 정률 할인
            discountAmount = orderAmount.multiply(this.discountValue)
                    .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_DOWN);

            // 최대 할인 금액 적용
            if (this.maximumDiscountAmount != null
                    && discountAmount.compareTo(this.maximumDiscountAmount) > 0) {
                discountAmount = this.maximumDiscountAmount;
            }
        }

        // 할인 금액이 주문 금액보다 클 수 없음
        if (discountAmount.compareTo(orderAmount) > 0) {
            discountAmount = orderAmount;
        }

        return discountAmount;
    }

    // 비즈니스 로직: 쿠폰 사용 가능 여부 확인
    public boolean isValid() {
        LocalDateTime now = LocalDateTime.now();
        return this.status == CouponStatus.ACTIVE
                && now.isAfter(this.validFrom)
                && now.isBefore(this.validUntil);
    }

    // 비즈니스 로직: 쿠폰 비활성화
    public void deactivate() {
        this.status = CouponStatus.INACTIVE;
    }

    // 비즈니스 로직: 쿠폰 활성화
    public void activate() {
        this.status = CouponStatus.ACTIVE;
    }
}

package com.hhplus.ecommerce.domain.order;

import com.hhplus.ecommerce.domain.coupon.OrderCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderCoupon> orderCoupons = new ArrayList<>();

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal finalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false)
    private LocalDateTime orderedAt;

    private LocalDateTime paidAt;

    private LocalDateTime cancelledAt;

    @Column(length = 500)
    private String cancellationReason;

    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @PrePersist
    protected void onCreate() {
        if (this.orderedAt == null) {
            this.orderedAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = OrderStatus.PENDING;
        }
    }

    // 비즈니스 로직: 결제 완료 처리
    public void completePay() {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("결제 대기 상태에서만 결제할 수 있습니다.");
        }
        this.status = OrderStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    // 비즈니스 로직: 주문 취소
    public void cancel(String reason) {
        if (this.status == OrderStatus.CANCELLED || this.status == OrderStatus.REFUNDED) {
            throw new IllegalStateException("이미 취소되거나 환불된 주문입니다.");
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancellationReason = reason;
    }

    // 비즈니스 로직: 환불 처리
    public void refund() {
        if (this.status != OrderStatus.PAID) {
            throw new IllegalStateException("결제 완료된 주문만 환불할 수 있습니다.");
        }
        this.status = OrderStatus.REFUNDED;
        this.cancelledAt = LocalDateTime.now();
    }

    // 비즈니스 로직: 주문 아이템 추가 (OrderItem 객체)
    public void addOrderItem(OrderItem orderItem) {
        this.orderItems.add(orderItem);
    }

    // 비즈니스 로직: 주문 아이템 추가 (Product와 수량)
    public void addOrderItem(Product product, Integer quantity) {
        OrderItem orderItem = OrderItem.builder()
                .order(this)
                .product(product)
                .quantity(quantity)
                .price(product.getPrice())
                .build();
        this.orderItems.add(orderItem);
    }

    // 비즈니스 로직: 쿠폰 적용
    public void applyCoupon(UserCoupon userCoupon, BigDecimal discountAmount) {
        OrderCoupon orderCoupon = OrderCoupon.of(this, userCoupon, discountAmount);
        this.orderCoupons.add(orderCoupon);
    }

    // 비즈니스 로직: 할인 적용
    public void applyDiscount(BigDecimal discountAmount) {
        if (discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("할인 금액은 0보다 작을 수 없습니다.");
        }
        if (discountAmount.compareTo(this.totalAmount) > 0) {
            throw new IllegalArgumentException("할인 금액은 총 금액보다 클 수 없습니다.");
        }
        this.discountAmount = discountAmount;
        this.finalAmount = this.totalAmount.subtract(discountAmount);
    }
}

package com.hhplus.ecommerce.domain.coupon;

import com.hhplus.ecommerce.domain.order.Order;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity                             // JPA관리 대상임을 명시(DB테이블과 매핑)
@Table(name = "order_coupons")      // 엔티티가 매핑될 테이블 이름 설정
@Getter                             // lombok >> 모든 필드의 getter생성
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 파라미터 없는 생성자 자동 생성
@AllArgsConstructor                 // 모든 필드를 받는 생성사 자동 생성
@Builder                            // 빌더 패턴 메서드 생성(유연한 객체 생성??)
public class OrderCoupon {

    @Id                                                 // PK임을 나타낸다.
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동증가방식으로 생성(auto increment와 같은역할)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)                  // N:1관계를 의미, LAZY는 필요할 때만 엔티티를 불러옴
    @JoinColumn(name = "order_id", nullable = false)    //
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_coupon_id", nullable = false)
    private UserCoupon userCoupon;

    @Column(nullable = false, precision = 15, scale = 2)    //
    private BigDecimal discountAmount;                      // 할인금액

    @Column(nullable = false)
    private LocalDateTime appliedAt;

    @PrePersist                                             // 엔티티가 처음 저장되기 전에 실행
    protected void onCreate() {
        if (this.appliedAt == null) {                       // 비어있다면
            this.appliedAt = LocalDateTime.now();           // 현재 시간으로 자동 저장
        }
    }

    // 정적 팩토리 메서드
    public static OrderCoupon of(Order order, UserCoupon userCoupon, BigDecimal discountAmount) {
        return OrderCoupon.builder()
                .order(order)
                .userCoupon(userCoupon)
                .discountAmount(discountAmount)
                .build();
    }
}

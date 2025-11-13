package com.hhplus.ecommerce.domain.product;

import com.hhplus.ecommerce.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_name", columnList = "name"),
        @Index(name = "idx_category_id", columnList = "category_id"),
        @Index(name = "idx_status_stock", columnList = "status, stock")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false)
    private Integer safetyStock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @Version
    private Long version; // 낙관적 락

    // 비즈니스 로직: 재고 증가
    public void increaseStock(Integer quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("증가 수량은 0보다 커야 합니다.");
        }
        this.stock += quantity;
        updateStatus();
    }

    // 비즈니스 로직: 재고 감소
    public void decreaseStock(Integer quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("감소 수량은 0보다 커야 합니다.");
        }
        if (this.stock < quantity) {
            throw new IllegalArgumentException("재고가 부족합니다.");
        }
        this.stock -= quantity;
        updateStatus();
    }

    // 비즈니스 로직: 재고 조정
    public void adjustStock(Integer newStock) {
        if (newStock < 0) {
            throw new IllegalArgumentException("재고는 0보다 작을 수 없습니다.");
        }
        this.stock = newStock;
        updateStatus();
    }

    // 비즈니스 로직: 가격 변경
    public void updatePrice(BigDecimal newPrice) {
        if (newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("가격은 0보다 커야 합니다.");
        }
        this.price = newPrice;
    }

    // 비즈니스 로직: 상태 업데이트
    private void updateStatus() {
        if (this.stock == 0) {
            this.status = ProductStatus.OUT_OF_STOCK;
        } else {
            this.status = ProductStatus.AVAILABLE;
        }
    }

    // 비즈니스 로직: 판매 중단
    public void discontinue() {
        this.status = ProductStatus.DISCONTINUED;
    }

    // 비즈니스 로직: 안전 재고 수준 확인
    public boolean isBelowSafetyStock() {
        return this.stock <= this.safetyStock;
    }

    // 비즈니스 로직: 재고 확인
    public boolean isAvailable() {
        return this.status == ProductStatus.AVAILABLE && this.stock > 0;
    }
}

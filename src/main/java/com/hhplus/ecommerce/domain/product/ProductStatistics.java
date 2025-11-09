package com.hhplus.ecommerce.domain.product;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_statistics",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "statistics_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ProductStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private LocalDate statisticsDate;

    @Column(nullable = false)
    private Integer salesCount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal salesAmount;

    @Column(nullable = false)
    private Integer viewCount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // 비즈니스 로직: 판매 집계
    public void addSales(Integer count, BigDecimal amount) {
        this.salesCount += count;
        this.salesAmount = this.salesAmount.add(amount);
    }

    // 비즈니스 로직: 조회수 증가
    public void incrementViewCount() {
        this.viewCount++;
    }
}

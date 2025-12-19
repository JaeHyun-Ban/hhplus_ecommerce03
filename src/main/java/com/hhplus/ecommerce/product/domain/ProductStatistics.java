package com.hhplus.ecommerce.product.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 상품 통계 (일별 집계)
 */
@Entity
@Table(name = "product_statistics",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "statistics_date"}),
        indexes = {
                @Index(name = "idx_product_date", columnList = "product_id, statistics_date"),
                @Index(name = "idx_statistics_date", columnList = "statistics_date")
        })
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

    public void addSales(Integer count, BigDecimal amount) {
        this.salesCount += count;
        this.salesAmount = this.salesAmount.add(amount);
    }

    public void incrementViewCount() {
        this.viewCount++;
    }
}

package com.hhplus.ecommerce.product.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity // JPA가 관리하는 엔티티 클래스임을 나타냅니다.
@Table(name = "product_statistics", // 'product_statistics'라는 이름의 데이터베이스 테이블과 매핑됩니다.
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "statistics_date"}), // 'product_id'와 'statistics_date' 조합의 유니크 제약조건을 설정합니다.
        indexes = { // 데이터베이스 조회의 성능 향상을 위한 인덱스를 설정합니다.
                @Index(name = "idx_product_date", columnList = "product_id, statistics_date"), // 상품 ID와 날짜 조합의 복합 인덱스
                @Index(name = "idx_statistics_date", columnList = "statistics_date") // 통계 날짜 단일 인덱스
        })
@Getter // Lombok: 모든 필드에 대한 getter 메서드를 자동으로 생성합니다.
@NoArgsConstructor(access = AccessLevel.PROTECTED) // Lombok: 파라미터가 없는 기본 생성자를 생성하며, 외부에서의 직접적인 생성을 막기 위해 접근 수준을 PROTECTED로 설정합니다.
@AllArgsConstructor // Lombok: 모든 필드를 인자로 받는 생성자를 자동으로 생성합니다.
@Builder // Lombok: 빌더 패턴을 사용하여 객체를 생성할 수 있게 합니다.
public class ProductStatistics {

    @Id // 이 필드가 테이블의 기본 키(Primary Key)임을 나타냅니다.
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 기본 키 생성을 데이터베이스의 AUTO_INCREMENT 정책에 위임합니다.
    private Long id; // 통계 데이터의 고유 식별자입니다.

    @ManyToOne(fetch = FetchType.LAZY) // Product 엔티티와 다대일(N:1) 관계를 맺습니다. LAZY 로딩으로 필요한 시점에만 상품 정보를 조회합니다.
    @JoinColumn(name = "product_id", nullable = false) // 외래 키(Foreign Key) 컬럼의 이름과 속성을 설정합니다. 'product_id'는 null일 수 없습니다.
    private Product product; // 이 통계가 속한 상품 엔티티입니다.

    @Column(nullable = false)
    private LocalDate statisticsDate; // 통계가 집계된 날짜입니다.

    @Column(nullable = false)
    private Integer salesCount; // 해당 날짜의 총 판매 수량입니다.

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal salesAmount; // 해당 날짜의 총 판매 금액입니다.

    @Column(nullable = false)
    private Integer viewCount; // 해당 날짜의 상품 조회수입니다.

    @Column(nullable = false, updatable = false) // 데이터베이스 컬럼과 매핑되며, null 값을 허용하지 않고, 한 번 생성되면 수정될 수 없습니다.
    private LocalDateTime createdAt; // 통계 데이터가 처음 생성된 시간입니다.

    @PrePersist // 엔티티가 데이터베이스에 처음 저장되기 전에 실행될 메서드임을 나타냅니다.
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(); // 생성 시간을 현재 시간으로 설정합니다.
    }

    // 비즈니스 로직: 판매 집계
    public void addSales(Integer count, BigDecimal amount) {
        this.salesCount += count; // 기존 판매 수량에 새로운 수량을 더합니다.
        this.salesAmount = this.salesAmount.add(amount); // 기존 판매 금액에 새로운 금액을 더합니다.
    }

    // 비즈니스 로직: 조회수 증가
    public void incrementViewCount() {
        this.viewCount++; // 조회수를 1 증가시킵니다.
    }
}

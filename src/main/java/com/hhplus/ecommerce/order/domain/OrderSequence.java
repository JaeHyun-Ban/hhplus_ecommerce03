package com.hhplus.ecommerce.order.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 주문 번호 시퀀스 엔티티
 *
 * Domain Layer - 도메인 엔티티
 *
 * 책임:
 * - 날짜별 주문 번호 시퀀스 관리
 * - 동시성 안전한 주문 번호 생성
 *
 * 동시성 제어:
 * - 비관적 락(PESSIMISTIC_WRITE)으로 시퀀스 증가 시 충돌 방지
 * - 날짜별로 독립적인 시퀀스 관리
 *
 * 예시:
 * - date: "2025-11-20", sequence: 1 → "ORD-20251120-000001"
 * - date: "2025-11-20", sequence: 2 → "ORD-20251120-000002"
 * - date: "2025-11-21", sequence: 1 → "ORD-20251121-000001"
 */
@Entity
@Table(name = "order_sequences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OrderSequence {

    /**
     * 주문 날짜 (PK)
     * Format: "yyyy-MM-dd" (예: "2025-11-20")
     */
    @Id
    @Column(name = "order_date", nullable = false, length = 10)
    private String date;

    /**
     * 해당 날짜의 주문 시퀀스
     * 1부터 시작하여 순차 증가
     */
    @Column(nullable = false)
    private Long sequence;

    // Note: @Version을 사용하지 않음
    // - 비관적 락(PESSIMISTIC_WRITE)으로 동시성 제어하므로 낙관적 락(@Version)은 불필요
    // - 두 락을 동시에 사용하면 충돌 발생 가능

    /**
     * 비즈니스 로직: 시퀀스 증가
     *
     * @return 증가된 시퀀스 번호
     */
    public Long incrementAndGet() {
        this.sequence++;
        return this.sequence;
    }

    /**
     * 정적 팩토리 메서드: 새로운 날짜의 시퀀스 생성
     *
     * @param date 주문 날짜
     * @return 시퀀스 1로 초기화된 OrderSequence
     */
    public static OrderSequence create(LocalDate date) {
        return OrderSequence.builder()
                .date(date.toString())  // "yyyy-MM-dd"
                .sequence(0L)            // 첫 주문은 incrementAndGet() 후 1
                .build();
    }

    /**
     * 주문 번호 생성
     *
     * @return 주문 번호 (예: "ORD-20251120-000001")
     */
    public String generateOrderNumber() {
        String datePart = this.date.replace("-", "");
        String sequencePart = String.valueOf(1000000 + this.sequence).substring(1);
        return new StringBuilder("ORD-")
            .append(datePart)
            .append("-")
            .append(sequencePart)
            .toString();
    }
}
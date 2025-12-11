package com.hhplus.ecommerce.order.infrastructure.persistence;

import com.hhplus.ecommerce.order.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Payment Repository
 *
 * Infrastructure Layer - 영속성 계층
 *
 * 책임:
 * - Payment 엔티티 CRUD
 * - 결제 정보 조회
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {
}

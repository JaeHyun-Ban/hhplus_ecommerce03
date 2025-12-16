package com.hhplus.ecommerce.payment.infrastructure.persistence;

import com.hhplus.ecommerce.payment.domain.Payment;
import com.hhplus.ecommerce.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

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

    /**
     * 주문 ID로 결제 정보 조회
     *
     * @param orderId 주문 ID
     * @return 결제 정보 (Optional)
     */
    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId")
    Optional<Payment> findByOrderId(@Param("orderId") Long orderId);

    /**
     * 결제 상태로 결제 목록 조회
     *
     * @param status 결제 상태
     * @return 해당 상태의 결제 목록
     */
    List<Payment> findByStatus(PaymentStatus status);

    /**
     * 결제 상태와 결제 수단으로 결제 목록 조회
     *
     * @param status 결제 상태
     * @param method 결제 수단
     * @return 해당 조건의 결제 목록
     */
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.method = :method")
    List<Payment> findByStatusAndMethod(@Param("status") PaymentStatus status,
                                        @Param("method") String method);
}

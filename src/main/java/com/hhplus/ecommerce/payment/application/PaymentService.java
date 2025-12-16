package com.hhplus.ecommerce.payment.application;

import com.hhplus.ecommerce.order.domain.Order;
import com.hhplus.ecommerce.payment.domain.Payment;
import com.hhplus.ecommerce.payment.domain.PaymentMethod;
import com.hhplus.ecommerce.payment.domain.PaymentStatus;
import com.hhplus.ecommerce.payment.infrastructure.persistence.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 결제 애플리케이션 서비스
 *
 * Application Layer - Use Case 실행 계층
 *
 * 책임:
 * - 결제 정보 생성
 * - 결제 완료 처리
 * - 결제 실패 처리
 * - 결제 취소 처리
 * - 결제 조회
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * 결제 정보 생성
     *
     * @param order 주문
     * @param amount 결제 금액
     * @param method 결제 수단
     * @return 생성된 결제 정보
     */
    @Transactional
    public Payment createPayment(Order order, BigDecimal amount, PaymentMethod method) {
        log.info("결제 정보 생성 시작 - orderId: {}, amount: {}, method: {}",
                order.getId(), amount, method);

        // 검증: 금액이 양수인지 확인
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
        }

        // Payment 엔티티 생성
        Payment payment = Payment.builder()
                .order(order)
                .amount(amount)
                .method(method)
                .status(PaymentStatus.PENDING)
                .build();

        Payment savedPayment = paymentRepository.save(payment);
        log.info("결제 정보 생성 완료 - paymentId: {}", savedPayment.getId());

        return savedPayment;
    }

    /**
     * 결제 완료 처리
     *
     * @param paymentId 결제 ID
     * @return 완료된 결제 정보
     */
    @Transactional
    public Payment completePayment(Long paymentId) {
        log.info("결제 완료 처리 시작 - paymentId: {}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다. paymentId: " + paymentId));

        // 도메인 로직 위임
        payment.complete();

        Payment completedPayment = paymentRepository.save(payment);
        log.info("결제 완료 처리 완료 - paymentId: {}, completedAt: {}",
                completedPayment.getId(), completedPayment.getCompletedAt());

        return completedPayment;
    }

    /**
     * 결제 실패 처리
     *
     * @param paymentId 결제 ID
     * @param reason 실패 사유
     * @return 실패 처리된 결제 정보
     */
    @Transactional
    public Payment failPayment(Long paymentId, String reason) {
        log.info("결제 실패 처리 시작 - paymentId: {}, reason: {}", paymentId, reason);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다. paymentId: " + paymentId));

        // 도메인 로직 위임
        payment.fail(reason);

        Payment failedPayment = paymentRepository.save(payment);
        log.info("결제 실패 처리 완료 - paymentId: {}", failedPayment.getId());

        return failedPayment;
    }

    /**
     * 결제 취소 처리
     *
     * @param paymentId 결제 ID
     * @return 취소된 결제 정보
     */
    @Transactional
    public Payment cancelPayment(Long paymentId) {
        log.info("결제 취소 처리 시작 - paymentId: {}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다. paymentId: " + paymentId));

        // 도메인 로직 위임
        payment.cancel();

        Payment cancelledPayment = paymentRepository.save(payment);
        log.info("결제 취소 처리 완료 - paymentId: {}", cancelledPayment.getId());

        return cancelledPayment;
    }

    /**
     * 결제 정보 조회 (ID)
     *
     * @param paymentId 결제 ID
     * @return 결제 정보
     */
    public Payment getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다. paymentId: " + paymentId));
    }

    /**
     * 결제 정보 조회 (Optional)
     *
     * @param paymentId 결제 ID
     * @return 결제 정보 (Optional)
     */
    public Optional<Payment> findPaymentById(Long paymentId) {
        return paymentRepository.findById(paymentId);
    }

    /**
     * 모든 결제 정보 조회
     *
     * @return 모든 결제 정보 목록
     */
    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    /**
     * 결제 상태 확인
     *
     * @param paymentId 결제 ID
     * @return 결제 상태
     */
    public PaymentStatus getPaymentStatus(Long paymentId) {
        Payment payment = getPayment(paymentId);
        return payment.getStatus();
    }

    /**
     * 결제 완료 여부 확인
     *
     * @param paymentId 결제 ID
     * @return 완료 여부
     */
    public boolean isPaymentCompleted(Long paymentId) {
        Payment payment = getPayment(paymentId);
        return payment.getStatus() == PaymentStatus.COMPLETED;
    }

    /**
     * 주문 ID로 결제 정보 조회
     *
     * @param orderId 주문 ID
     * @return 결제 정보
     */
    public Payment getPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주문의 결제 정보를 찾을 수 없습니다. orderId: " + orderId));
    }

    /**
     * 주문 ID로 결제 정보 조회 (Optional)
     *
     * @param orderId 주문 ID
     * @return 결제 정보 (Optional)
     */
    public Optional<Payment> findPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    /**
     * 결제 상태별 결제 목록 조회
     *
     * @param status 결제 상태
     * @return 해당 상태의 결제 목록
     */
    public List<Payment> getPaymentsByStatus(PaymentStatus status) {
        return paymentRepository.findByStatus(status);
    }
}

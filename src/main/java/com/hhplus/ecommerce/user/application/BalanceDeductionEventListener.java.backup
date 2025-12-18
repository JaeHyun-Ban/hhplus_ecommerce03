package com.hhplus.ecommerce.user.application;

import com.hhplus.ecommerce.common.application.DomainEventStoreService;
import com.hhplus.ecommerce.common.domain.DomainEventStore;
import com.hhplus.ecommerce.common.domain.event.BalanceDeductionPayload;
import com.hhplus.ecommerce.order.domain.Order;
import com.hhplus.ecommerce.payment.domain.Payment;
import com.hhplus.ecommerce.order.domain.event.OrderCompletedEvent;
import com.hhplus.ecommerce.order.infrastructure.persistence.OrderRepository;
import com.hhplus.ecommerce.product.application.BalanceDeductionEvent;
import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.domain.StockHistory;
import com.hhplus.ecommerce.product.domain.StockTransactionType;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.StockHistoryRepository;
import com.hhplus.ecommerce.user.domain.BalanceHistory;
import com.hhplus.ecommerce.user.domain.BalanceTransactionType;
import com.hhplus.ecommerce.user.domain.User;
import com.hhplus.ecommerce.user.infrastructure.persistence.BalanceHistoryRepository;
import com.hhplus.ecommerce.user.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * 잔액 차감 이벤트 리스너
 *
 * Application Layer - 이벤트 핸들러
 *
 * 책임:
 * - 재고 차감 성공 후 잔액 차감 처리
 * - TransactionalEventListener의 AFTER_COMMIT으로 실행
 *
 * 실행 시점:
 * - 재고 차감 트랜잭션이 성공적으로 커밋된 후
 * - BalanceDeductionEvent 발행 시
 *
 * 처리 내용:
 * 1. 사용자 잔액 차감 (비관적 락)
 * 2. 잔액 이력 기록
 * 3. Payment 완료 처리
 * 4. 성공 시: OrderCompletedEvent 발행 (쿠폰 사용, 인기상품 집계)
 * 5. 실패 시: 재고 복구 + 주문 취소 보상 트랜잭션
 *
 * 트랜잭션:
 * - REQUIRES_NEW: 독립적인 새 트랜잭션 생성
 * - 재고 차감 트랜잭션과 분리되어 실행
 *
 * 장점:
 * - 재고 차감과 잔액 처리 분리
 * - 잔액 처리 실패 시 재고 복구 가능
 * - 비동기 처리 가능 (향후 확장)
 *
 * 주의사항:
 * - AFTER_COMMIT이므로 실패 시 보상 트랜잭션 필요
 * - 실패 시 재고 복구 + 주문 취소 필요
 * - 이벤트 소싱으로 실패 추적
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BalanceDeductionEventListener {

    private final UserRepository userRepository;
    private final BalanceHistoryRepository balanceHistoryRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final DomainEventStoreService eventStoreService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 재고 차감 성공 후 잔액 차감 처리
     *
     * 실행 조건:
     * - 재고 차감 트랜잭션 커밋 성공
     *
     * 처리 내용:
     * 1. 사용자 잔액 차감
     * 2. 잔액 이력 기록
     * 3. Payment 완료 처리
     * 4. 성공 시: OrderCompletedEvent 발행
     * 5. 실패 시: 재고 복구 + 주문 취소 보상 트랜잭션
     *
     * 예외 처리:
     * - 잔액 부족 시: 재고 복구 + 주문 취소
     * - 사용자 조회 실패 시: 재고 복구 + 주문 취소
     * - 기타 예외: 이벤트 소싱으로 실패 추적
     *
     * @param event 잔액 차감 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleBalanceDeduction(BalanceDeductionEvent event) {
        log.info("[이벤트] 잔액 차감 시작 - orderId: {}, userId: {}, amount: {}",
                 event.getOrderId(), event.getUserId(), event.getAmount());

        try {
            // Step 1: 사용자 조회 (비관적 락)
            User user = userRepository.findByIdWithLock(event.getUserId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "사용자를 찾을 수 없습니다. userId: " + event.getUserId()));

            // Step 2: 잔액 차감 (도메인 로직 - 잔액 부족 시 예외 발생)
            BigDecimal balanceBefore = user.getBalance();
            user.useBalance(event.getAmount());
            userRepository.save(user);

            // Step 3: 잔액 이력 기록
            BalanceHistory history = BalanceHistory.builder()
                .user(user)
                .type(BalanceTransactionType.USE)
                .amount(event.getAmount())
                .balanceBefore(balanceBefore)
                .balanceAfter(user.getBalance())
                .description("주문 결제: " + event.getOrderNumber())
                .createdAt(LocalDateTime.now())
                .build();

            balanceHistoryRepository.save(history);

            log.info("[이벤트] 잔액 차감 성공 - orderId: {}, userId: {}, balanceAfter: {}",
                     event.getOrderId(), event.getUserId(), user.getBalance());

            // Step 4: Order 및 Payment 완료 처리 (PENDING → PAID)
            completeOrderAndPayment(event.getOrderId());

            // Step 5: 성공 시 OrderCompletedEvent 발행 (쿠폰 사용, 인기상품 집계)
            OrderCompletedEvent completedEvent = OrderCompletedEvent.builder()
                .orderId(event.getOrderId())
                .userCouponId(event.getUserCouponId())
                .discountAmount(event.getDiscountAmount())
                .userId(event.getUserId())
                .orderProducts(event.getOrderProducts().stream()
                    .map(p -> OrderCompletedEvent.OrderProductInfo.builder()
                        .productId(p.getProductId())
                        .quantity(p.getQuantity())
                        .build())
                    .toList())
                .build();

            eventPublisher.publishEvent(completedEvent);
            log.info("[이벤트] 주문 완료 이벤트 발행 - orderId: {}", event.getOrderId());

        } catch (IllegalStateException e) {
            // 잔액 부족 등 도메인 로직 예외
            log.error("[이벤트] 잔액 차감 실패 (잔액 부족) - orderId: {}, reason: {}",
                      event.getOrderId(), e.getMessage());

            // 보상 트랜잭션: 재고 복구 + 주문 취소
            restoreStockAndCancelOrder(event, "잔액 차감 실패: " + e.getMessage());

            // 보상 트랜잭션: 이벤트 소싱을 통한 실패 이벤트 저장
            saveToDomainEventStore(event, e.getMessage());

        } catch (Exception e) {
            // 기타 예외: 로그 기록 및 보상 트랜잭션
            log.error("[이벤트] 잔액 차감 중 예외 발생 - orderId: {}",
                      event.getOrderId(), e);

            // 보상 트랜잭션: 재고 복구 + 주문 취소
            restoreStockAndCancelOrder(event, "잔액 차감 중 예외 발생: " + e.getMessage());

            // 보상 트랜잭션: 이벤트 소싱을 통한 실패 이벤트 저장
            saveToDomainEventStore(event, e.getMessage());
        }
    }

    /**
     * Order 및 Payment 완료 처리
     *
     * Order 상태를 PENDING → PAID로 변경
     * Payment 상태를 PENDING → COMPLETED로 변경
     *
     * @param orderId 주문 ID
     */
    private void completeOrderAndPayment(Long orderId) {
        try {
            Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                    "주문을 찾을 수 없습니다. orderId: " + orderId));

            // Order 완료 처리 (PENDING → PAID)
            order.completePay();

            // Payment 완료 처리 (PENDING → COMPLETED)
            if (order.getPayment() != null) {
                Payment payment = order.getPayment();
                payment.complete();
                log.info("[이벤트] 결제 완료 처리 - orderId: {}, paymentId: {}, orderStatus: PAID",
                         orderId, payment.getId());
            }

            orderRepository.save(order);

        } catch (Exception e) {
            log.error("[이벤트] 주문 및 결제 완료 처리 실패 - orderId: {}", orderId, e);
            // Order/Payment 완료 실패는 로그만 기록 (잔액 차감은 성공)
        }
    }

    /**
     * 보상 트랜잭션: 재고 복구 + 주문 취소
     *
     * 잔액 차감 실패 시:
     * 1. 재고를 원래대로 복구
     * 2. 주문을 취소 상태로 변경
     *
     * @param event 잔액 차감 이벤트
     * @param reason 취소 사유
     */
    private void restoreStockAndCancelOrder(BalanceDeductionEvent event, String reason) {
        try {
            // Step 1: 재고 복구
            for (var productInfo : event.getOrderProducts()) {
                Product product = productRepository.findByIdWithLock(productInfo.getProductId())
                    .orElse(null);

                if (product != null) {
                    int stockBefore = product.getStock();
                    product.increaseStock(productInfo.getQuantity());
                    productRepository.save(product);

                    // 재고 이력 기록
                    StockHistory history = StockHistory.builder()
                        .product(product)
                        .type(StockTransactionType.INCREASE)
                        .quantity(productInfo.getQuantity())
                        .stockBefore(stockBefore)
                        .stockAfter(product.getStock())
                        .reason("잔액 차감 실패로 재고 복구: " + event.getOrderNumber())
                        .createdAt(LocalDateTime.now())
                        .build();

                    stockHistoryRepository.save(history);

                    log.info("[보상 트랜잭션] 재고 복구 완료 - productId: {}, quantity: {}, stockAfter: {}",
                             productInfo.getProductId(), productInfo.getQuantity(), product.getStock());
                }
            }

            // Step 2: 주문 취소
            Order order = orderRepository.findById(event.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "주문을 찾을 수 없습니다. orderId: " + event.getOrderId()));

            order.cancel(reason);
            orderRepository.save(order);

            log.info("[보상 트랜잭션] 주문 취소 완료 - orderId: {}, reason: {}", event.getOrderId(), reason);

        } catch (Exception e) {
            log.error("[보상 트랜잭션] 재고 복구 및 주문 취소 실패 - orderId: {}", event.getOrderId(), e);
            // 보상 트랜잭션 실패는 로그만 기록
            // 수동 처리 필요
        }
    }

    /**
     * 도메인 이벤트 스토어에 실패 이벤트 저장 (보상 트랜잭션)
     *
     * 이벤트 소싱 패턴:
     * - DomainEventStoreService가 REQUIRES_NEW로 독립적인 트랜잭션 실행
     * - 이벤트 저장 실패 시에도 로그만 기록
     * - 자동 재시도 메커니즘이 나중에 처리
     *
     * @param event 잔액 차감 이벤트
     * @param failureReason 실패 사유
     */
    private void saveToDomainEventStore(BalanceDeductionEvent event, String failureReason) {
        // 잔액 차감 페이로드 생성
        BalanceDeductionPayload payload = BalanceDeductionPayload.builder()
            .orderId(event.getOrderId())
            .orderNumber(event.getOrderNumber())
            .userId(event.getUserId())
            .amount(event.getAmount())
            .failureReason(failureReason)
            .build();

        // 이벤트 스토어에 저장 (이벤트 소싱)
        eventStoreService.saveEvent(
            DomainEventStore.EventType.BALANCE_CHARGED,
            event.getOrderId(),  // Aggregate ID: Order ID
            "Order",             // Aggregate Type
            payload
        );
    }
}

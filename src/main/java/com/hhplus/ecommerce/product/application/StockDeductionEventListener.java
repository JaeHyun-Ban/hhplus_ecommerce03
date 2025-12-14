package com.hhplus.ecommerce.product.application;

import com.hhplus.ecommerce.common.application.DomainEventStoreService;
import com.hhplus.ecommerce.common.domain.DomainEventStore;
import com.hhplus.ecommerce.common.domain.event.StockDeductionPayload;
import com.hhplus.ecommerce.order.domain.Order;
import com.hhplus.ecommerce.order.domain.event.OrderCreatedEvent;
import com.hhplus.ecommerce.order.infrastructure.persistence.OrderRepository;
import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.domain.StockHistory;
import com.hhplus.ecommerce.product.domain.StockTransactionType;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.StockHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * 재고 차감 이벤트 리스너
 *
 * Application Layer - 이벤트 핸들러
 *
 * 책임:
 * - 주문 생성 후 재고 차감 처리
 * - TransactionalEventListener의 AFTER_COMMIT으로 실행
 *
 * 실행 시점:
 * - 주문 트랜잭션이 성공적으로 커밋된 후
 * - OrderCreatedEvent 발행 시
 *
 * 처리 내용:
 * 1. 주문 상품별 재고 차감 (낙관적 락)
 * 2. 재고 이력 기록
 * 3. 성공 시: BalanceDeductionEvent 발행 (다음 단계)
 * 4. 실패 시: 주문 취소 보상 트랜잭션
 *
 * 트랜잭션:
 * - REQUIRES_NEW: 독립적인 새 트랜잭션 생성
 * - 주문 트랜잭션과 분리되어 실행
 *
 * 장점:
 * - 주문 트랜잭션과 재고 처리 분리
 * - 재고 처리 실패 시 주문은 생성되었으나 취소 처리
 * - 비동기 처리 가능 (향후 확장)
 *
 * 주의사항:
 * - AFTER_COMMIT이므로 실패 시 보상 트랜잭션 필요
 * - 실패 시 주문 취소 처리 필요
 * - 이벤트 소싱으로 실패 추적
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockDeductionEventListener {

    private final ProductRepository productRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final OrderRepository orderRepository;
    private final DomainEventStoreService eventStoreService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 주문 생성 후 재고 차감 처리
     *
     * 실행 조건:
     * - 주문 트랜잭션 커밋 성공
     *
     * 처리 내용:
     * 1. 주문된 각 상품의 재고 차감
     * 2. 재고 이력 기록
     * 3. 성공 시: BalanceDeductionEvent 발행
     * 4. 실패 시: 보상 트랜잭션 (주문 취소)
     *
     * 예외 처리:
     * - 재고 부족 시: 주문 취소 보상 트랜잭션
     * - 상품 조회 실패 시: 주문 취소 보상 트랜잭션
     * - 기타 예외: 이벤트 소싱으로 실패 추적
     *
     * @param event 주문 생성 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("[이벤트] 재고 차감 시작 - orderId: {}, 상품 수: {}",
                 event.getOrderId(), event.getOrderProducts().size());

        try {
            // Step 1: 각 주문 상품별 재고 차감
            for (OrderCreatedEvent.OrderProductInfo productInfo : event.getOrderProducts()) {
                // 상품 조회 (낙관적 락)
                Product product = productRepository.findByIdWithLock(productInfo.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                        "상품을 찾을 수 없습니다. productId: " + productInfo.getProductId()));

                // 재고 차감 (도메인 로직 - 재고 부족 시 예외 발생)
                int stockBefore = product.getStock();
                product.decreaseStock(productInfo.getQuantity());
                productRepository.save(product);

                // Step 2: 재고 이력 기록
                StockHistory history = StockHistory.builder()
                    .product(product)
                    .type(StockTransactionType.DECREASE)
                    .quantity(productInfo.getQuantity())
                    .stockBefore(stockBefore)
                    .stockAfter(product.getStock())
                    .reason("주문: " + event.getOrderNumber())
                    .createdAt(LocalDateTime.now())
                    .build();

                stockHistoryRepository.save(history);

                log.debug("[이벤트] 재고 차감 완료 - productId: {}, quantity: {}, stockAfter: {}",
                         productInfo.getProductId(), productInfo.getQuantity(), product.getStock());
            }

            log.info("[이벤트] 재고 차감 성공 - orderId: {}, 처리된 상품 수: {}",
                     event.getOrderId(), event.getOrderProducts().size());

            // Step 3: 성공 시 다음 단계 이벤트 발행 (BalanceDeductionEvent)
            BalanceDeductionEvent balanceEvent = BalanceDeductionEvent.builder()
                .orderId(event.getOrderId())
                .orderNumber(event.getOrderNumber())
                .userId(event.getUserId())
                .amount(event.getFinalAmount())
                .userCouponId(event.getUserCouponId())
                .discountAmount(event.getDiscountAmount())
                .orderProducts(event.getOrderProducts())
                .build();

            eventPublisher.publishEvent(balanceEvent);
            log.info("[이벤트] 잔액 차감 이벤트 발행 - orderId: {}", event.getOrderId());

        } catch (IllegalStateException e) {
            // 재고 부족 등 도메인 로직 예외
            log.error("[이벤트] 재고 차감 실패 (재고 부족) - orderId: {}, reason: {}",
                      event.getOrderId(), e.getMessage());

            // 보상 트랜잭션: 주문 취소
            cancelOrderCompensation(event.getOrderId(), "재고 차감 실패: " + e.getMessage());

            // 보상 트랜잭션: 이벤트 소싱을 통한 실패 이벤트 저장
            saveToDomainEventStore(event, e.getMessage());

        } catch (Exception e) {
            // 기타 예외: 로그 기록 및 보상 트랜잭션
            log.error("[이벤트] 재고 차감 중 예외 발생 - orderId: {}",
                      event.getOrderId(), e);

            // 보상 트랜잭션: 주문 취소
            cancelOrderCompensation(event.getOrderId(), "재고 차감 중 예외 발생: " + e.getMessage());

            // 보상 트랜잭션: 이벤트 소싱을 통한 실패 이벤트 저장
            saveToDomainEventStore(event, e.getMessage());
        }
    }

    /**
     * 보상 트랜잭션: 주문 취소
     *
     * 재고 차감 실패 시 주문을 취소 상태로 변경
     * (PAID → CANCELLED)
     *
     * @param orderId 주문 ID
     * @param reason 취소 사유
     */
    private void cancelOrderCompensation(Long orderId, String reason) {
        try {
            Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                    "주문을 찾을 수 없습니다. orderId: " + orderId));

            // 주문 취소 (도메인 로직)
            order.cancel(reason);
            orderRepository.save(order);

            log.info("[보상 트랜잭션] 주문 취소 완료 - orderId: {}, reason: {}", orderId, reason);

        } catch (Exception e) {
            log.error("[보상 트랜잭션] 주문 취소 실패 - orderId: {}", orderId, e);
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
     * @param event 주문 생성 이벤트
     * @param failureReason 실패 사유
     */
    private void saveToDomainEventStore(OrderCreatedEvent event, String failureReason) {
        // 재고 차감 페이로드 생성
        StockDeductionPayload payload = StockDeductionPayload.builder()
            .orderId(event.getOrderId())
            .orderNumber(event.getOrderNumber())
            .userId(event.getUserId())
            .orderProducts(event.getOrderProducts().stream()
                .map(p -> StockDeductionPayload.OrderProductInfo.builder()
                    .productId(p.getProductId())
                    .quantity(p.getQuantity())
                    .build())
                .collect(Collectors.toList()))
            .failureReason(failureReason)
            .build();

        // 이벤트 스토어에 저장 (이벤트 소싱)
        eventStoreService.saveEvent(
            DomainEventStore.EventType.PRODUCT_STOCK_DECREASED,
            event.getOrderId(),  // Aggregate ID: Order ID
            "Order",             // Aggregate Type
            payload
        );
    }
}
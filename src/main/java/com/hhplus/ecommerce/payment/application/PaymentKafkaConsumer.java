package com.hhplus.ecommerce.payment.application;

import com.hhplus.ecommerce.common.application.DomainEventStoreService;
import com.hhplus.ecommerce.common.domain.DomainEventStore;
import com.hhplus.ecommerce.common.domain.event.BalanceDeductionPayload;
import com.hhplus.ecommerce.config.KafkaConfig;
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
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 처리 Kafka Consumer (stock-events → payment-events)
 *
 * Kafka Topic: stock-events → payment-events
 * Consumer Group: payment-consumer-group
 *
 * 처리 흐름:
 * 1. stock-events 토픽에서 BalanceDeductionEvent 수신
 * 2. 잔액 차감 처리 (비관적 락)
 * 3. 잔액 이력 기록
 * 4. Order 및 Payment 완료 처리
 * 5. 성공 시: payment-events 토픽으로 OrderCompletedEvent 발행
 * 6. 실패 시: 보상 트랜잭션 (재고 복구 + 주문 취소)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentKafkaConsumer {

    private final UserRepository userRepository;
    private final BalanceHistoryRepository balanceHistoryRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final DomainEventStoreService eventStoreService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(
        topics = KafkaConfig.TOPIC_STOCK_EVENTS,
        groupId = KafkaConfig.GROUP_PAYMENT_CONSUMER,
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(
        include = {org.springframework.dao.CannotAcquireLockException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 1.5, maxDelay = 500)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleBalanceDeduction(
            @Payload BalanceDeductionEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("[Kafka] 잔액 차감 시작 - partition: {}, offset: {}, orderId: {}, userId: {}, amount: {}",
                 partition, offset, event.getOrderId(), event.getUserId(), event.getAmount());

        try {
            // 잔액 차감
            User user = userRepository.findByIdWithLock(event.getUserId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "사용자를 찾을 수 없습니다. userId: " + event.getUserId()));

            BigDecimal balanceBefore = user.getBalance();
            user.useBalance(event.getAmount());
            userRepository.save(user);

            // 잔액 이력 기록
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

            log.info("[Kafka] 잔액 차감 성공 - orderId: {}, userId: {}, balanceAfter: {}",
                     event.getOrderId(), event.getUserId(), user.getBalance());

            // Order 및 Payment 완료 처리
            completeOrderAndPayment(event.getOrderId());

            // payment-events 토픽으로 OrderCompletedEvent 발행
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

            kafkaTemplate.send(KafkaConfig.TOPIC_PAYMENT_EVENTS, event.getOrderId().toString(), completedEvent);
            log.info("[Kafka] payment-events 발행 - orderId: {}", event.getOrderId());

            ack.acknowledge();

        } catch (IllegalStateException e) {
            log.error("[Kafka] 잔액 차감 실패 - orderId: {}, reason: {}",
                      event.getOrderId(), e.getMessage());
            restoreStockAndCancelOrder(event, "잔액 차감 실패: " + e.getMessage());
            saveToDomainEventStore(event, e.getMessage());
            ack.acknowledge();

        } catch (Exception e) {
            log.error("[Kafka] 잔액 차감 중 예외 발생 - orderId: {}", event.getOrderId(), e);
            restoreStockAndCancelOrder(event, "잔액 차감 중 예외 발생: " + e.getMessage());
            saveToDomainEventStore(event, e.getMessage());
            throw e;
        }
    }

    private void completeOrderAndPayment(Long orderId) {
        try {
            Order order = orderRepository.findById(orderId).orElseThrow();
            order.completePay();
            if (order.getPayment() != null) {
                Payment payment = order.getPayment();
                payment.complete();
                log.info("[Kafka] 결제 완료 처리 - orderId: {}, paymentId: {}", orderId, payment.getId());
            }
            orderRepository.save(order);
        } catch (Exception e) {
            log.error("[Kafka] 주문 및 결제 완료 처리 실패 - orderId: {}", orderId, e);
        }
    }

    private void restoreStockAndCancelOrder(BalanceDeductionEvent event, String reason) {
        try {
            for (var productInfo : event.getOrderProducts()) {
                Product product = productRepository.findByIdWithLock(productInfo.getProductId()).orElse(null);
                if (product != null) {
                    int stockBefore = product.getStock();
                    product.increaseStock(productInfo.getQuantity());
                    productRepository.save(product);

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
                }
            }

            Order order = orderRepository.findById(event.getOrderId()).orElse(null);
            if (order != null) {
                order.cancel(reason);
                orderRepository.save(order);
                log.info("[보상] 재고 복구 및 주문 취소 완료 - orderId: {}", event.getOrderId());
            }
        } catch (Exception e) {
            log.error("[보상] 재고 복구 및 주문 취소 실패 - orderId: {}", event.getOrderId(), e);
        }
    }

    private void saveToDomainEventStore(BalanceDeductionEvent event, String failureReason) {
        BalanceDeductionPayload payload = BalanceDeductionPayload.builder()
            .orderId(event.getOrderId())
            .orderNumber(event.getOrderNumber())
            .userId(event.getUserId())
            .amount(event.getAmount())
            .failureReason(failureReason)
            .build();

        eventStoreService.saveEvent(
            DomainEventStore.EventType.BALANCE_CHARGED,
            event.getOrderId(),
            "Order",
            payload
        );
    }
}

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

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * 재고 차감 Kafka Consumer (order-events → stock-events)
 *
 * Kafka Topic: order-events → stock-events
 * Consumer Group: stock-consumer-group
 *
 * 처리 흐름:
 * 1. order-events 토픽에서 OrderCreatedEvent 수신
 * 2. 재고 차감 처리 (낙관적 락)
 * 3. 재고 이력 기록
 * 4. 성공 시: stock-events 토픽으로 BalanceDeductionEvent 발행
 * 5. 실패 시: 보상 트랜잭션 (주문 취소)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockKafkaConsumer {

    private final ProductRepository productRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final OrderRepository orderRepository;
    private final DomainEventStoreService eventStoreService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(
        topics = "order-events",
        groupId = "stock-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(
        include = {org.springframework.orm.ObjectOptimisticLockingFailureException.class},
        maxAttempts = 5,
        backoff = @Backoff(delay = 50, multiplier = 1.5, maxDelay = 200)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleOrderCreated(
            @Payload OrderCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("[Kafka] 재고 차감 시작 - partition: {}, offset: {}, orderId: {}, 상품 수: {}",
                 partition, offset, event.getOrderId(), event.getOrderProducts().size());

        try {
            // 재고 차감
            for (OrderCreatedEvent.OrderProductInfo productInfo : event.getOrderProducts()) {
                Product product = productRepository.findByIdWithLock(productInfo.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                        "상품을 찾을 수 없습니다. productId: " + productInfo.getProductId()));

                int stockBefore = product.getStock();
                product.decreaseStock(productInfo.getQuantity());
                productRepository.save(product);

                // 재고 이력 기록
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
            }

            log.info("[Kafka] 재고 차감 성공 - orderId: {}", event.getOrderId());

            // stock-events 토픽으로 BalanceDeductionEvent 발행
            BalanceDeductionEvent balanceEvent = BalanceDeductionEvent.builder()
                .orderId(event.getOrderId())
                .orderNumber(event.getOrderNumber())
                .userId(event.getUserId())
                .amount(event.getFinalAmount())
                .userCouponId(event.getUserCouponId())
                .discountAmount(event.getDiscountAmount())
                .orderProducts(event.getOrderProducts())
                .build();

            kafkaTemplate.send("stock-events", event.getOrderId().toString(), balanceEvent);
            log.info("[Kafka] stock-events 발행 - orderId: {}", event.getOrderId());

            ack.acknowledge();

        } catch (IllegalStateException e) {
            log.error("[Kafka] 재고 차감 실패 - orderId: {}, reason: {}",
                      event.getOrderId(), e.getMessage());
            cancelOrderCompensation(event.getOrderId(), "재고 차감 실패: " + e.getMessage());
            saveToDomainEventStore(event, e.getMessage());
            ack.acknowledge();  // 재시도하지 않고 커밋

        } catch (Exception e) {
            log.error("[Kafka] 재고 차감 중 예외 발생 - orderId: {}", event.getOrderId(), e);
            cancelOrderCompensation(event.getOrderId(), "재고 차감 중 예외 발생: " + e.getMessage());
            saveToDomainEventStore(event, e.getMessage());
            throw e;  // 재시도 또는 DLQ
        }
    }

    private void cancelOrderCompensation(Long orderId, String reason) {
        try {
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order != null) {
                order.cancel(reason);
                orderRepository.save(order);
                log.info("[보상] 주문 취소 완료 - orderId: {}", orderId);
            }
        } catch (Exception e) {
            log.error("[보상] 주문 취소 실패 - orderId: {}", orderId, e);
        }
    }

    private void saveToDomainEventStore(OrderCreatedEvent event, String failureReason) {
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

        eventStoreService.saveEvent(
            DomainEventStore.EventType.PRODUCT_STOCK_DECREASED,
            event.getOrderId(),
            "Order",
            payload
        );
    }
}

package com.hhplus.ecommerce.order.application;

import com.hhplus.ecommerce.coupon.domain.UserCoupon;
import com.hhplus.ecommerce.coupon.infrastructure.persistence.UserCouponRepository;
import com.hhplus.ecommerce.order.domain.Order;
import com.hhplus.ecommerce.order.domain.event.OrderCompletedEvent;
import com.hhplus.ecommerce.order.infrastructure.persistence.OrderRepository;
import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRedisRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 완료 후처리 Kafka Consumer (payment-events)
 *
 * Kafka Topic: payment-events
 * Consumer Group: order-completed-consumer-group
 *
 * 처리 흐름:
 * 1. payment-events 토픽에서 OrderCompletedEvent 수신
 * 2. 쿠폰 사용 처리 (있는 경우)
 * 3. 인기상품 집계 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCompletedKafkaConsumer {

    private final UserCouponRepository userCouponRepository;
    private final OrderRepository orderRepository;
    private final ProductRedisRepository productRedisRepository;
    private final ProductRepository productRepository;

    @KafkaListener(
        topics = "payment-events",
        groupId = "order-completed-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleOrderCompleted(
            @Payload OrderCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("[Kafka] 주문 완료 후처리 시작 - partition: {}, offset: {}, orderId: {}",
                 partition, offset, event.getOrderId());

        try {
            // 1. 쿠폰 사용 처리
            if (event.hasCoupon()) {
                processCouponUsage(event);
            }

            // 2. 인기상품 집계
            processPopularProductAggregation(event);

            ack.acknowledge();

        } catch (Exception e) {
            log.error("[Kafka] 주문 완료 후처리 실패 - orderId: {}", event.getOrderId(), e);
            ack.acknowledge();  // 실패해도 커밋 (쿠폰/인기상품은 주문 성공에 영향 없음)
        }
    }

    private void processCouponUsage(OrderCompletedEvent event) {
        try {
            log.info("[Kafka] 쿠폰 사용 처리 시작 - orderId: {}, userCouponId: {}",
                     event.getOrderId(), event.getUserCouponId());

            UserCoupon userCoupon = userCouponRepository.findById(event.getUserCouponId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "사용자 쿠폰을 찾을 수 없습니다. userCouponId: " + event.getUserCouponId()));

            if (userCoupon.canUse()) {
                userCoupon.markAsUsed();
                userCouponRepository.save(userCoupon);

                Order order = orderRepository.findById(event.getOrderId()).orElse(null);
                if (order != null) {
                    order.applyCoupon(userCoupon, event.getDiscountAmount());
                    orderRepository.save(order);
                }

                log.info("[Kafka] 쿠폰 사용 완료 - orderId: {}, userCouponId: {}",
                         event.getOrderId(), event.getUserCouponId());
            } else {
                log.warn("[Kafka] 쿠폰 이미 사용됨 - orderId: {}, userCouponId: {}",
                         event.getOrderId(), event.getUserCouponId());
            }

        } catch (Exception e) {
            log.error("[Kafka] 쿠폰 사용 처리 실패 - orderId: {}, error: {}", event.getOrderId(), e.getMessage());
            // 쿠폰 사용 실패는 로그만 기록 (주문은 이미 완료됨)
        }
    }

    private void processPopularProductAggregation(OrderCompletedEvent event) {
        try {
            log.info("[Kafka] 인기상품 집계 시작 - orderId: {}, 상품 수: {}",
                     event.getOrderId(), event.getOrderProducts().size());

            for (OrderCompletedEvent.OrderProductInfo productInfo : event.getOrderProducts()) {
                try {
                    productRedisRepository.incrementPopularityScore(
                        productInfo.getProductId(),
                        productInfo.getQuantity()
                    );

                    Product product = productRepository.findById(productInfo.getProductId()).orElse(null);
                    if (product != null) {
                        productRedisRepository.cacheProductInfo(product);
                    }

                    log.debug("[Kafka] 인기도 스코어 증가 완료 - productId: {}, quantity: {}",
                             productInfo.getProductId(), productInfo.getQuantity());

                } catch (Exception e) {
                    log.warn("[Kafka] 인기도 스코어 증가 실패 (Redis 장애) - productId: {}, error: {}",
                             productInfo.getProductId(), e.getMessage());
                }
            }

            log.info("[Kafka] 인기상품 집계 완료 - orderId: {}", event.getOrderId());

        } catch (Exception e) {
            log.error("[Kafka] 인기상품 집계 실패 - orderId: {}, error: {}", event.getOrderId(), e.getMessage());
            // 인기상품 집계 실패는 로그만 기록 (주문은 이미 완료됨, Redis 장애 시 나중에 배치로 보정 가능)
        }
    }
}

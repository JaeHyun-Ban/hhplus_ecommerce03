package com.hhplus.ecommerce.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka 설정
 *
 * 책임:
 * - Kafka Topic 생성
 * - Producer/Consumer 설정
 *
 * Topic 목록:
 * - order-events: 주문 생성/완료 이벤트
 * - payment-events: 결제 이벤트
 * - stock-events: 재고 차감 이벤트
 * - coupon-events: 쿠폰 발급/사용 이벤트
 */
@EnableKafka
@Configuration
public class KafkaConfig {

    // Kafka Topic 상수
    public static final String TOPIC_ORDER_EVENTS = "order-events";
    public static final String TOPIC_PAYMENT_EVENTS = "payment-events";
    public static final String TOPIC_STOCK_EVENTS = "stock-events";
    public static final String TOPIC_COUPON_EVENTS = "coupon-events";

    // Kafka Consumer Group ID 상수
    public static final String GROUP_COUPON_CONSUMER = "coupon-consumer-group";
    public static final String GROUP_STOCK_CONSUMER = "stock-consumer-group";
    public static final String GROUP_PAYMENT_CONSUMER = "payment-consumer-group";
    public static final String GROUP_ORDER_COMPLETED_CONSUMER = "order-completed-consumer-group";

    /**
     * 주문 이벤트 토픽
     * - 파티션: 3개 (동시 처리 성능 향상)
     * - 복제본: 1개 (로컬 개발 환경용)
     */
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(TOPIC_ORDER_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 결제 이벤트 토픽
     */
    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name(TOPIC_PAYMENT_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 재고 이벤트 토픽
     */
    @Bean
    public NewTopic stockEventsTopic() {
        return TopicBuilder.name(TOPIC_STOCK_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 쿠폰 이벤트 토픽
     */
    @Bean
    public NewTopic couponEventsTopic() {
        return TopicBuilder.name(TOPIC_COUPON_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}

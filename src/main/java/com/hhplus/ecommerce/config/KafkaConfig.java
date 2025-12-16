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

    /**
     * 주문 이벤트 토픽
     * - 파티션: 3개 (동시 처리 성능 향상)
     * - 복제본: 1개 (로컬 개발 환경용)
     */
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 결제 이벤트 토픽
     */
    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name("payment-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 재고 이벤트 토픽
     */
    @Bean
    public NewTopic stockEventsTopic() {
        return TopicBuilder.name("stock-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 쿠폰 이벤트 토픽
     */
    @Bean
    public NewTopic couponEventsTopic() {
        return TopicBuilder.name("coupon-events")
                .partitions(3)
                .replicas(1)
                .build();
    }
}

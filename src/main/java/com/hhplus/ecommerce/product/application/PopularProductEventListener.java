package com.hhplus.ecommerce.product.application;

import com.hhplus.ecommerce.common.application.DomainEventStoreService;
import com.hhplus.ecommerce.common.domain.DomainEventStore;
import com.hhplus.ecommerce.common.domain.event.PopularProductAggregationPayload;
import com.hhplus.ecommerce.order.domain.event.OrderCompletedEvent;
import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRedisRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 인기상품 집계 이벤트 리스너
 *
 * Application Layer - 이벤트 핸들러
 *
 * 책임:
 * - 주문 완료 후 인기상품 집계 처리
 * - TransactionalEventListener의 AFTER_COMMIT으로 실행
 *
 * 실행 시점:
 * - 주문 트랜잭션이 성공적으로 커밋된 후
 * - OrderCompletedEvent 발행 시
 *
 * 처리 내용:
 * 1. Redis Sorted Set에 상품별 인기도 스코어 증가
 * 2. Redis Hash에 상품 정보 캐싱
 *
 * 트랜잭션:
 * - REQUIRES_NEW: 독립적인 새 트랜잭션 생성
 * - 주문 트랜잭션과 분리되어 실행
 *
 * 장점:
 * - 주문 트랜잭션과 Redis 처리 분리
 * - Redis 장애 시에도 주문은 성공 유지
 * - 비동기 처리 가능 (향후 확장)
 *
 * 주의사항:
 * - AFTER_COMMIT이므로 실패 시 주문 롤백 불가
 * - Redis 장애는 주문 프로세스에 영향 없음
 * - 실패 시 재시도 또는 배치로 보정 가능
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PopularProductEventListener {

    private final ProductRedisRepository productRedisRepository;
    private final ProductRepository productRepository;
    private final DomainEventStoreService eventStoreService;

    /**
     * 주문 완료 후 인기상품 집계 처리
     *
     * 실행 조건:
     * - 주문 트랜잭션 커밋 성공
     *
     * 처리 내용:
     * 1. 주문된 각 상품의 인기도 스코어 증가
     * 2. 상품 정보를 Redis 캐시에 저장
     *
     * 예외 처리:
     * - Redis 장애 시: 로그만 기록 (주문은 정상 완료)
     * - 상품 조회 실패 시: 해당 상품만 스킵
     *
     * @param event 주문 완료 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        log.info("[이벤트] 인기상품 집계 시작 - orderId: {}, 상품 수: {}",
                 event.getOrderId(), event.getOrderProducts().size());

        try {
            // 각 주문 상품별로 인기도 스코어 증가
            for (OrderCompletedEvent.OrderProductInfo productInfo : event.getOrderProducts()) {
                try {
                    // Step 1: 인기도 스코어 증가 (Redis Sorted Set)
                    productRedisRepository.incrementPopularityScore(
                        productInfo.getProductId(),
                        productInfo.getQuantity()
                    );

                    // Step 2: 상품 정보 캐싱 (Redis Hash)
                    // 상품 정보 조회 (읽기 전용)
                    Product product = productRepository.findById(productInfo.getProductId())
                        .orElse(null);

                    if (product != null) {
                        productRedisRepository.cacheProductInfo(product);
                        log.debug("[이벤트] 인기상품 집계 완료 - productId: {}, quantity: {}",
                                 productInfo.getProductId(), productInfo.getQuantity());
                    } else {
                        log.warn("[이벤트] 상품을 찾을 수 없음 - productId: {}", productInfo.getProductId());
                    }

                } catch (Exception e) {
                    // 개별 상품 처리 실패 시 다음 상품 계속 처리
                    log.error("[이벤트] 인기상품 집계 실패 (개별 상품) - productId: {}, orderId: {}",
                              productInfo.getProductId(), event.getOrderId(), e);
                }
            }

            log.info("[이벤트] 인기상품 집계 완료 - orderId: {}, 처리된 상품 수: {}",
                     event.getOrderId(), event.getOrderProducts().size());

        } catch (Exception e) {
            // 전체 처리 실패 시 로그만 기록
            // Redis 장애 등으로 인한 실패는 주문 프로세스에 영향 없음
            log.error("[이벤트] 인기상품 집계 중 예외 발생 - orderId: {}",
                      event.getOrderId(), e);

            // 보상 트랜잭션: 이벤트 소싱을 통한 실패 이벤트 저장
            saveToDomainEventStore(event);

            // 주문은 이미 완료되었으므로 예외를 던지지 않음
            // 대신 보상 트랜잭션으로 실패 이벤트 저장 후 재시도 메커니즘 동작
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
     * @param event 주문 완료 이벤트
     */
    private void saveToDomainEventStore(OrderCompletedEvent event) {
        // 인기상품 집계 페이로드 생성
        PopularProductAggregationPayload payload = PopularProductAggregationPayload.builder()
            .orderId(event.getOrderId())
            .userId(event.getUserId())
            .orderProducts(event.getOrderProducts().stream()
                .map(p -> PopularProductAggregationPayload.OrderProductInfo.builder()
                    .productId(p.getProductId())
                    .quantity(p.getQuantity())
                    .build())
                .toList())
            .build();

        // 이벤트 스토어에 저장 (이벤트 소싱)
        eventStoreService.saveEvent(
            DomainEventStore.EventType.POPULAR_PRODUCT_AGGREGATION,
            event.getOrderId(),  // Aggregate ID: Order ID
            "Order",             // Aggregate Type
            payload
        );
    }
}

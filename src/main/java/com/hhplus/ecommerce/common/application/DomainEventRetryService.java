package com.hhplus.ecommerce.common.application;

import com.hhplus.ecommerce.common.constants.SchedulerConstants;
import com.hhplus.ecommerce.common.domain.DomainEventStore;
import com.hhplus.ecommerce.common.domain.event.CouponUsagePayload;
import com.hhplus.ecommerce.common.domain.event.EventPayload;
import com.hhplus.ecommerce.common.domain.event.PopularProductAggregationPayload;
import com.hhplus.ecommerce.common.domain.event.StockDeductionPayload;
import com.hhplus.ecommerce.common.domain.event.BalanceDeductionPayload;
import com.hhplus.ecommerce.common.infrastructure.DomainEventStoreRepository;
import com.hhplus.ecommerce.coupon.domain.UserCoupon;
import com.hhplus.ecommerce.coupon.domain.UserCouponStatus;
import com.hhplus.ecommerce.coupon.infrastructure.persistence.UserCouponRepository;
import com.hhplus.ecommerce.order.domain.Order;
import com.hhplus.ecommerce.order.infrastructure.persistence.OrderRepository;
import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.domain.StockHistory;
import com.hhplus.ecommerce.product.domain.StockTransactionType;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRedisRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.StockHistoryRepository;
import com.hhplus.ecommerce.user.domain.BalanceHistory;
import com.hhplus.ecommerce.user.domain.BalanceTransactionType;
import com.hhplus.ecommerce.user.domain.User;
import com.hhplus.ecommerce.user.infrastructure.persistence.BalanceHistoryRepository;
import com.hhplus.ecommerce.user.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 도메인 이벤트 재시도 서비스
 *
 * Application Layer - 서비스
 *
 * 책임:
 * - 실패한 도메인 이벤트 재시도
 * - 스케줄러로 주기적 실행
 * - 분산 락으로 중복 실행 방지
 * - 이벤트 타입별 재시도 로직 실행
 *
 * 실행 주기:
 * - 1분마다 실행
 *
 * 재시도 전략:
 * - Exponential Backoff (1분 → 5분 → 15분)
 * - 최대 3회 재시도
 * - 최종 실패 시 수동 처리 필요
 *
 * Use Cases:
 * - 모든 도메인 이벤트 처리 보상 트랜잭션
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DomainEventRetryService {

    private final DomainEventStoreRepository eventStoreRepository;
    private final DomainEventStoreService eventStoreService;
    private final RedissonClient redissonClient;

    // 쿠폰 사용 처리를 위한 의존성
    private final UserCouponRepository userCouponRepository;
    private final OrderRepository orderRepository;

    // 인기상품 집계를 위한 의존성
    private final ProductRedisRepository productRedisRepository;
    private final ProductRepository productRepository;

    // 재고 차감을 위한 의존성
    private final StockHistoryRepository stockHistoryRepository;

    // 잔액 차감을 위한 의존성
    private final UserRepository userRepository;
    private final BalanceHistoryRepository balanceHistoryRepository;

    private static final int BATCH_SIZE = 100;

    /**
     * 실패한 이벤트 재시도 (스케줄러)
     *
     * 실행 주기: 1분마다
     * 분산 락: 중복 실행 방지
     *
     * 프로세스:
     * 1. 분산 락 획득
     * 2. 재시도 가능한 이벤트 조회
     * 3. 각 이벤트 재시도 (이벤트 타입별 처리)
     * 4. 성공 시 완료 처리, 실패 시 재시도 횟수 증가
     */
    @Scheduled(cron = SchedulerConstants.Cron.EVERY_MINUTE)
    public void retryFailedEvents() {
        RLock lock = redissonClient.getLock(SchedulerConstants.LockKeys.COUPON_EVENT_RETRY);

        try {
            // 분산 락 획득 시도 (대기 없음, 1분 유지)
            boolean isLocked = lock.tryLock(0, 60, TimeUnit.SECONDS);

            if (!isLocked) {
                log.debug("[재시도 스케줄러] 다른 인스턴스에서 이미 실행 중");
                return;
            }

            log.info("[재시도 스케줄러] 도메인 이벤트 재시도 시작");

            // 재시도 가능한 이벤트 조회
            List<DomainEventStore> retryableEvents = eventStoreRepository
                .findRetryableEvents(LocalDateTime.now(), BATCH_SIZE);

            if (retryableEvents.isEmpty()) {
                log.debug("[재시도 스케줄러] 재시도할 이벤트 없음");
                return;
            }

            log.info("[재시도 스케줄러] 재시도 대상: {}건", retryableEvents.size());

            int successCount = 0;
            int failCount = 0;

            // 각 이벤트 재시도
            for (DomainEventStore event : retryableEvents) {
                try {
                    retryEvent(event);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("[재시도 스케줄러] 이벤트 재시도 중 예외 발생 - eventId: {}, eventType: {}",
                              event.getId(), event.getEventType(), e);
                }
            }

            log.info("[재시도 스케줄러] 재시도 완료 - 성공: {}건, 실패: {}건", successCount, failCount);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[재시도 스케줄러] 인터럽트 발생", e);
        } catch (Exception e) {
            log.error("[재시도 스케줄러] 예외 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 개별 이벤트 재시도
     *
     * 프로세스:
     * 1. 재시도 시작 (상태 변경)
     * 2. 이벤트 타입별 처리 로직 실행
     * 3. 성공 시 완료 처리
     * 4. 실패 시 재시도 횟수 증가
     *
     * @param event 실패한 이벤트
     */
    @Transactional
    public void retryEvent(DomainEventStore event) {
        log.info("[재시도] 시작 - eventId: {}, eventType: {}, aggregateId: {}, retryCount: {}/{}",
                 event.getId(), event.getEventType(), event.getAggregateId(),
                 event.getRetryCount(), event.getMaxRetryCount());

        try {
            // Step 1: 재시도 시작
            event.startProcessing();
            eventStoreRepository.save(event);

            // Step 2: 이벤트 타입별 처리
            switch (event.getEventType()) {
                case COUPON_USAGE -> processCouponUsage(event);
                case POPULAR_PRODUCT_AGGREGATION -> processPopularProductAggregation(event);
                case PRODUCT_STOCK_DECREASED -> processStockDeduction(event);
                case BALANCE_CHARGED -> processBalanceDeduction(event);
                default -> throw new IllegalArgumentException(
                    "지원하지 않는 이벤트 타입: " + event.getEventType());
            }

            // Step 3: 성공 - 완료 처리
            event.markAsCompleted();
            eventStoreRepository.save(event);

            log.info("[재시도] 성공 - eventId: {}, eventType: {}, aggregateId: {}",
                     event.getId(), event.getEventType(), event.getAggregateId());

        } catch (Exception e) {
            // Step 4: 실패 - 재시도 횟수 증가
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            event.markAsFailed(reason);
            eventStoreRepository.save(event);

            log.warn("[재시도] 실패 - eventId: {}, eventType: {}, aggregateId: {}, retryCount: {}/{}, status: {}, reason: {}",
                     event.getId(), event.getEventType(), event.getAggregateId(),
                     event.getRetryCount(), event.getMaxRetryCount(),
                     event.getStatus(), reason);

            // 최종 실패 시 알림
            if (event.getStatus() == DomainEventStore.EventStatus.FAILED) {
                log.error("[재시도] 최종 실패 - 수동 처리 필요 - eventId: {}, eventType: {}, aggregateId: {}",
                          event.getId(), event.getEventType(), event.getAggregateId());
                // TODO: 알림 시스템 연동 (Slack, Email 등)
            }

            throw e;
        }
    }

    /**
     * 쿠폰 사용 처리 (재시도 로직)
     *
     * @param event 이벤트
     */
    private void processCouponUsage(DomainEventStore event) {
        // 페이로드 역직렬화
        Optional<CouponUsagePayload> payloadOpt = eventStoreService
            .deserializePayload(event, CouponUsagePayload.class);

        if (payloadOpt.isEmpty()) {
            throw new IllegalArgumentException("쿠폰 사용 페이로드 역직렬화 실패");
        }

        CouponUsagePayload payload = payloadOpt.get();

        // Step 1: UserCoupon 조회
        UserCoupon userCoupon = userCouponRepository.findById(payload.getUserCouponId())
            .orElseThrow(() -> new IllegalArgumentException(
                "사용자 쿠폰을 찾을 수 없습니다. userCouponId: " + payload.getUserCouponId()));

        // Step 2: 쿠폰 사용 처리 (멱등성 체크)
        if (userCoupon.getStatus() == UserCouponStatus.USED) {
            log.warn("[재시도] 쿠폰이 이미 사용됨 - userCouponId: {}, orderId: {}",
                     payload.getUserCouponId(), payload.getOrderId());
            // 이미 사용된 쿠폰이면 성공으로 간주
            return;
        }

        userCoupon.markAsUsed();
        userCouponRepository.save(userCoupon);

        // Step 3: Order 조회
        Order order = orderRepository.findById(payload.getOrderId())
            .orElseThrow(() -> new IllegalArgumentException(
                "주문을 찾을 수 없습니다. orderId: " + payload.getOrderId()));

        // Step 4: 주문에 쿠폰 적용 기록
        order.applyCoupon(userCoupon, payload.getDiscountAmount());
        orderRepository.save(order);

        log.info("[재시도] 쿠폰 사용 처리 완료 - orderId: {}, userCouponId: {}, discountAmount: {}",
                 payload.getOrderId(), payload.getUserCouponId(), payload.getDiscountAmount());
    }

    /**
     * 인기상품 집계 처리 (재시도 로직)
     *
     * @param event 이벤트
     */
    private void processPopularProductAggregation(DomainEventStore event) {
        // 페이로드 역직렬화
        Optional<PopularProductAggregationPayload> payloadOpt = eventStoreService
            .deserializePayload(event, PopularProductAggregationPayload.class);

        if (payloadOpt.isEmpty()) {
            throw new IllegalArgumentException("인기상품 집계 페이로드 역직렬화 실패");
        }

        PopularProductAggregationPayload payload = payloadOpt.get();

        // 각 주문 상품별로 인기도 스코어 증가
        for (PopularProductAggregationPayload.OrderProductInfo productInfo : payload.getOrderProducts()) {
            // Step 1: 인기도 스코어 증가 (Redis Sorted Set)
            productRedisRepository.incrementPopularityScore(
                productInfo.getProductId(),
                productInfo.getQuantity()
            );

            // Step 2: 상품 정보 캐싱 (Redis Hash)
            Product product = productRepository.findById(productInfo.getProductId())
                .orElse(null);

            if (product != null) {
                productRedisRepository.cacheProductInfo(product);
                log.debug("[재시도] 인기상품 집계 완료 - productId: {}, quantity: {}",
                         productInfo.getProductId(), productInfo.getQuantity());
            } else {
                log.warn("[재시도] 상품을 찾을 수 없음 - productId: {}", productInfo.getProductId());
            }
        }

        log.info("[재시도] 인기상품 집계 완료 - orderId: {}, 처리된 상품 수: {}",
                 payload.getOrderId(), payload.getOrderProducts().size());
    }

    /**
     * 재고 차감 처리 (재시도 로직)
     *
     * @param event 이벤트
     */
    private void processStockDeduction(DomainEventStore event) {
        // 페이로드 역직렬화
        Optional<StockDeductionPayload> payloadOpt = eventStoreService
            .deserializePayload(event, StockDeductionPayload.class);

        if (payloadOpt.isEmpty()) {
            throw new IllegalArgumentException("재고 차감 페이로드 역직렬화 실패");
        }

        StockDeductionPayload payload = payloadOpt.get();

        // 각 주문 상품별 재고 차감
        for (StockDeductionPayload.OrderProductInfo productInfo : payload.getOrderProducts()) {
            // 상품 조회 (낙관적 락)
            Product product = productRepository.findByIdWithLock(productInfo.getProductId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "상품을 찾을 수 없습니다. productId: " + productInfo.getProductId()));

            // 재고 차감 (도메인 로직)
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
                .reason("주문 재시도: " + payload.getOrderNumber())
                .createdAt(LocalDateTime.now())
                .build();

            stockHistoryRepository.save(history);

            log.debug("[재시도] 재고 차감 완료 - productId: {}, quantity: {}, stockAfter: {}",
                     productInfo.getProductId(), productInfo.getQuantity(), product.getStock());
        }

        log.info("[재시도] 재고 차감 완료 - orderId: {}, 처리된 상품 수: {}",
                 payload.getOrderId(), payload.getOrderProducts().size());
    }

    /**
     * 잔액 차감 처리 (재시도 로직)
     *
     * @param event 이벤트
     */
    private void processBalanceDeduction(DomainEventStore event) {
        // 페이로드 역직렬화
        Optional<BalanceDeductionPayload> payloadOpt = eventStoreService
            .deserializePayload(event, BalanceDeductionPayload.class);

        if (payloadOpt.isEmpty()) {
            throw new IllegalArgumentException("잔액 차감 페이로드 역직렬화 실패");
        }

        BalanceDeductionPayload payload = payloadOpt.get();

        // Step 1: 사용자 조회 (비관적 락)
        User user = userRepository.findByIdWithLock(payload.getUserId())
            .orElseThrow(() -> new IllegalArgumentException(
                "사용자를 찾을 수 없습니다. userId: " + payload.getUserId()));

        // Step 2: 잔액 차감 (도메인 로직)
        java.math.BigDecimal balanceBefore = user.getBalance();
        user.useBalance(payload.getAmount());
        userRepository.save(user);

        // Step 3: 잔액 이력 기록
        BalanceHistory history = BalanceHistory.builder()
            .user(user)
            .type(BalanceTransactionType.USE)
            .amount(payload.getAmount())
            .balanceBefore(balanceBefore)
            .balanceAfter(user.getBalance())
            .description("주문 결제 재시도: " + payload.getOrderNumber())
            .createdAt(LocalDateTime.now())
            .build();

        balanceHistoryRepository.save(history);

        log.info("[재시도] 잔액 차감 완료 - orderId: {}, userId: {}, amount: {}, balanceAfter: {}",
                 payload.getOrderId(), payload.getUserId(), payload.getAmount(), user.getBalance());
    }

    /**
     * 최종 실패한 이벤트 조회 (관리자용)
     *
     * @return 최종 실패 이벤트 목록
     */
    @Transactional(readOnly = true)
    public List<DomainEventStore> getFailedEvents() {
        return eventStoreRepository.findByStatus(DomainEventStore.EventStatus.FAILED);
    }

    /**
     * 수동 재시도 (관리자용)
     *
     * @param eventId 실패한 이벤트 ID
     */
    @Transactional
    public void manualRetry(Long eventId) {
        DomainEventStore event = eventStoreRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException(
                "실패한 이벤트를 찾을 수 없습니다. eventId: " + eventId));

        log.info("[수동 재시도] 시작 - eventId: {}, eventType: {}, aggregateId: {}",
                 eventId, event.getEventType(), event.getAggregateId());

        // 최종 실패 상태인 경우 상태 초기화
        if (event.getStatus() == DomainEventStore.EventStatus.FAILED) {
            event.resetForManualRetry();
            eventStoreRepository.save(event);
        }

        retryEvent(event);
    }
}

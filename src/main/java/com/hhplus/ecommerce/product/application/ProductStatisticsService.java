package com.hhplus.ecommerce.product.application;

import com.hhplus.ecommerce.order.domain.Order;
import com.hhplus.ecommerce.order.domain.OrderItem;
import com.hhplus.ecommerce.order.domain.OrderStatus;
import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.domain.ProductStatistics;
import com.hhplus.ecommerce.order.infrastructure.persistence.OrderRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 상품 통계 서비스
 *
 * Application Layer - 배치 작업 계층
 *
 * 책임:
 * - 일일 상품 판매 통계 집계
 * - 상품별 판매량, 판매금액 계산
 * - ProductStatistics 엔티티 생성/업데이트
 *
 * 실행 시점:
 * - 매일 새벽 1시 스케줄러에 의해 실행
 * - 전일(D-1) 주문 데이터를 집계하여 통계 생성
 *
 * 집계 대상:
 * - status = PAID (결제 완료된 주문만)
 * - 주문 항목별 상품, 수량, 금액
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductStatisticsService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductStatisticsRepository productStatisticsRepository;

    /**
     * 전일 상품 통계 집계
     *
     * 배치 작업:
     * - 매일 새벽 1시에 전일 주문 데이터 집계
     * - 상품별 판매량 및 판매금액 계산
     * - ProductStatistics 테이블에 저장
     *
     * 처리 흐름:
     * 1. 전일(D-1) 날짜 계산
     * 2. 전일 결제 완료 주문 조회
     * 3. 주문 항목별로 상품 통계 집계
     * 4. 상품별로 그룹핑하여 합산
     * 5. ProductStatistics 엔티티 생성 또는 업데이트
     *
     * 멱등성 보장:
     * - findByProductIdAndDate()로 기존 통계 확인
     * - 이미 존재하면 업데이트, 없으면 새로 생성
     *
     * @param targetDate 집계 대상 날짜 (전일)
     * @return 집계된 상품 수
     */
    public int aggregateDailyStatistics(LocalDate targetDate) {
        log.info("[배치] 일일 통계 집계 시작 - 대상 날짜: {}", targetDate);

        // Step 1: 전일 결제 완료 주문 조회
        LocalDateTime startOfDay = targetDate.atStartOfDay();
        LocalDateTime endOfDay = targetDate.atTime(LocalTime.MAX);
        List<Order> orders = orderRepository.findByOrderedAtBetween(startOfDay, endOfDay);

        if (orders.isEmpty()) {
            log.info("[배치] 집계할 주문이 없습니다 - 날짜: {}", targetDate);
            return 0;
        }

        // Step 2: 상품별 판매 통계 집계 (productId -> {salesCount, salesAmount})
        Map<Long, ProductSalesData> productSalesMap = new HashMap<>();

        for (Order order : orders) {
            // 결제 완료된 주문만 집계
            if (order.getStatus() != OrderStatus.PAID) {
                continue;
            }

            // 주문 항목별로 집계
            for (OrderItem orderItem : order.getOrderItems()) {
                Long productId = orderItem.getProduct().getId();
                Integer quantity = orderItem.getQuantity();
                BigDecimal amount = orderItem.getPrice().multiply(BigDecimal.valueOf(quantity));

                productSalesMap.putIfAbsent(productId, new ProductSalesData());
                ProductSalesData salesData = productSalesMap.get(productId);
                salesData.addSales(quantity, amount);
            }
        }

        log.info("[배치] 집계할 상품 수: {} - 날짜: {}", productSalesMap.size(), targetDate);

        // Step 3: ProductStatistics 엔티티 생성 또는 업데이트
        int savedCount = 0;
        for (Map.Entry<Long, ProductSalesData> entry : productSalesMap.entrySet()) {
            Long productId = entry.getKey();
            ProductSalesData salesData = entry.getValue();

            // 상품 조회
            Product product = productRepository.findById(productId)
                    .orElse(null);

            if (product == null) {
                log.warn("[배치] 상품을 찾을 수 없습니다 - productId: {}", productId);
                continue;
            }

            // 기존 통계 확인 (멱등성 보장)
            ProductStatistics statistics = productStatisticsRepository
                    .findByProductIdAndDate(productId, targetDate)
                    .orElse(null);

            if (statistics != null) {
                // 기존 통계가 있으면 업데이트
                log.debug("[배치] 기존 통계 업데이트 - productId: {}, 날짜: {}", productId, targetDate);
                statistics.addSales(salesData.salesCount, salesData.salesAmount);
            } else {
                // 새 통계 생성
                statistics = ProductStatistics.builder()
                        .product(product)
                        .statisticsDate(targetDate)
                        .salesCount(salesData.salesCount)
                        .salesAmount(salesData.salesAmount)
                        .viewCount(0) // 초기값
                        .build();

                log.debug("[배치] 새 통계 생성 - productId: {}, 날짜: {}, 판매량: {}, 판매금액: {}",
                        productId, targetDate, salesData.salesCount, salesData.salesAmount);
            }

            productStatisticsRepository.save(statistics);
            savedCount++;
        }

        log.info("[배치] 일일 통계 집계 완료 - 날짜: {}, 저장된 상품 수: {}", targetDate, savedCount);
        return savedCount;
    }

    /**
     * 상품 판매 데이터 (집계용 내부 클래스)
     */
    private static class ProductSalesData {
        private Integer salesCount = 0;
        private BigDecimal salesAmount = BigDecimal.ZERO;

        public void addSales(Integer count, BigDecimal amount) {
            this.salesCount += count;
            this.salesAmount = this.salesAmount.add(amount);
        }
    }
}

package com.hhplus.ecommerce.product.application;

import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.domain.ProductStatistics;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 상품 애플리케이션 서비스
 *
 * Application Layer - Use Case 실행 계층
 *
 * 책임:
 * - UC-003: 상품 목록 조회
 * - UC-004: 상품 상세 조회
 * - UC-006: 인기 상품 조회 (Redis 분산락 + 캐시)
 *
 * 레이어 의존성:
 * - Infrastructure Layer: ProductRepository, ProductStatisticsRepository
 * - Domain Layer: Product, ProductStatistics
 *
 * 동시성 제어:
 * - 인기 상품 캐시 갱신: Redisson 분산락
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductStatisticsRepository productStatisticsRepository;
    private final com.hhplus.ecommerce.product.infrastructure.persistence.ProductRedisRepository productRedisRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;

    // Cache Constants
    private static final String CACHE_NAME_PRODUCT_INFO = "product-info";
    private static final String CACHE_KEY_POPULAR_PRODUCTS_TOP5 = "cache:popular:products:top5";
    private static final long CACHE_TTL_POPULAR_PRODUCTS_TOP5_MINUTES = 10L;

    // Scheduler Constants
    private static final String LOCK_KEY_POPULAR_PRODUCTS_REFRESH = "lock:popular:products:refresh";

    /**
     * 상품 목록 조회
     *
     * Use Case: UC-003
     * - 판매 가능한 상품만 조회
     * - 페이징 지원
     *
     * 필터:
     * - status = AVAILABLE
     * - stock > 0
     *
     * @param pageable 페이징 정보
     * @return 상품 페이지
     */
    public Page<Product> getAvailableProducts(Pageable pageable) {
        log.info("[UC-003] 상품 목록 조회 - page: {}, size: {}",
                 pageable.getPageNumber(), pageable.getPageSize());

        return productRepository.findAvailableProducts(pageable);
    }

    /**
     * 카테고리별 상품 목록 조회
     *
     * Use Case: UC-003 (변형)
     *
     * @param categoryId 카테고리 ID
     * @param pageable 페이징 정보
     * @return 상품 페이지
     */
    public Page<Product> getProductsByCategory(Long categoryId, Pageable pageable) {
        log.info("[UC-003] 카테고리별 상품 조회 - categoryId: {}", categoryId);

        return productRepository.findByCategoryId(categoryId, pageable);
    }

    /**
     * 상품 상세 조회 (캐싱 적용)
     *
     * Use Case: UC-004
     * - 상품 정보 조회
     *
     * 캐시 전략:
     * - Cache Key: product:info:{productId}
     * - TTL: 1시간
     * - Cache Evict: 상품 수정 시
     *
     * @param productId 상품 ID
     * @return 상품 상세 정보
     */
    @Cacheable(value = CACHE_NAME_PRODUCT_INFO, key = "#productId")
    public Product getProduct(Long productId) {
        log.info("[UC-004] DB에서 상품 조회 - productId: {}", productId);

        return productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));
    }

    /**
     * 인기 상품 조회 (최근 3일 판매량 기준 TOP 5)
     *
     * Use Case: UC-006
     *
     * 집계 기준:
     * - 최근 3일간 판매 통계
     * - 판매량(salesCount) 합계 기준
     * - 상위 5개
     *
     * 캐시 전략:
     * - Redis 캐시 사용 (TTL: 10분)
     * - Cache Miss 시 분산락으로 중복 집계 방지
     * - Cache Stampede 방지
     *
     * 동시성 제어:
     * - Redis Pub/Sub 분산락 사용
     * - 다중 서버 환경에서 하나의 서버만 캐시 갱신
     *
     * @return 인기 상품 목록 (최대 5개)
     */
    public List<Product> getPopularProducts() {
        log.info("[UC-006] 인기 상품 조회 - 캐시 확인");

        // 1. 캐시 조회
        List<Product> cachedProducts = getCachedPopularProducts();
        if (cachedProducts != null) {
            log.info("[UC-006] 캐시 히트 - {} 개 상품 반환", cachedProducts.size());
            return cachedProducts;
        }

        // 2. 캐시 미스 - 분산락으로 갱신 (하나의 서버만 실행)
        log.info("[UC-006] 캐시 미스 - 분산락으로 캐시 갱신 시도");
        return refreshPopularProductsCache();
    }

    /**
     * 인기 상품 캐시 갱신 (Redisson 분산락 적용)
     *
     * 실행 순서:
     * 1. Redisson RLock 획득 시도
     * 2. 트랜잭션 시작 (@Transactional)
     * 3. Double-Check: 대기 중 다른 서버가 갱신했을 수 있음
     * 4. DB 집계 쿼리 실행
     * 5. 캐시 저장
     * 6. 트랜잭션 종료
     * 7. RLock 해제
     *
     * @return 인기 상품 목록
     */
    public List<Product> refreshPopularProductsCache() {
        // redisson의 분산락 객체를 가져온다.
        RLock lock = redissonClient.getLock(LOCK_KEY_POPULAR_PRODUCTS_REFRESH);

        try {
            // 락 획득 시도: 5초 대기, 15초 후 자동 해제
            boolean isLocked = lock.tryLock(5, 15, TimeUnit.SECONDS);

            if (!isLocked) {
                log.warn("[UC-006] 분산락 획득 실패 - 다른 서버가 캐시 갱신 중");
                throw new IllegalStateException("인기 상품 캐시 갱신 중입니다. 잠시 후 다시 시도해주세요.");
            }

            log.info("[UC-006] 캐시 갱신 시작 - Redisson 분산락 획득 완료");

            // Double-Check Pattern: 대기 중 다른 서버가 갱신했을 수 있음
            List<Product> cachedProducts = getCachedPopularProducts();
            if (cachedProducts != null) {
                log.info("[UC-006] Double-Check: 이미 다른 서버가 캐시 갱신 완료");
                return cachedProducts;
            }

            // DB 집계 실행
            log.info("[UC-006] DB 집계 시작");
            List<Product> products = fetchPopularProductsFromDBWithTransaction();

            // 캐시 저장
            redisTemplate.opsForValue().set(
                CACHE_KEY_POPULAR_PRODUCTS_TOP5,
                products,
                Duration.ofMinutes(CACHE_TTL_POPULAR_PRODUCTS_TOP5_MINUTES)
            );

            log.info("[UC-006] 캐시 갱신 완료 - {} 개 상품 저장", products.size());
            return products;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[UC-006] 락 획득 중 인터럽트 발생", e);
            throw new IllegalStateException("락 획득 중 오류 발생", e);
        } finally {
            // 락 해제
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("[UC-006] Redisson 분산락 해제 완료");
            }
        }
    }

    /**
     * DB에서 인기 상품 집계 (트랜잭션 적용)
     *
     * @return 인기 상품 목록
     */
    @Transactional(readOnly = true)
    public List<Product> fetchPopularProductsFromDBWithTransaction() {
        return fetchPopularProductsFromDB();
    }

    /**
     * 캐시에서 인기 상품 조회
     *
     * @return 캐시된 상품 목록, 없으면 null
     */
    @SuppressWarnings("unchecked")
    private List<Product> getCachedPopularProducts() {
        Object cached = redisTemplate.opsForValue().get(CACHE_KEY_POPULAR_PRODUCTS_TOP5);
        return cached != null ? (List<Product>) cached : null;
    }

    /**
     * DB에서 인기 상품 집계
     *
     * @return 인기 상품 목록
     */
    private List<Product> fetchPopularProductsFromDB() {
        // 최근 3일 날짜 범위
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(2); // 오늘 포함 3일

        // 인기 상품 ID 조회
        List<Long> topProductIds = productStatisticsRepository
            .findTopProductIdsByDateRange(startDate, endDate, 5);

        // 상품 정보 조회
        if (topProductIds.isEmpty()) {
            log.info("[UC-006] 통계 데이터 없음, 기본 목록 반환");
            // 통계 데이터가 없으면 최신 상품 5개 반환
            return productRepository.findAvailableProducts(
                org.springframework.data.domain.PageRequest.of(0, 5)
            ).getContent();
        }

        // ID 순서를 유지하며 상품 조회
        List<Product> products = productRepository.findAllById(topProductIds);

        // 원래 순서 유지 (판매량 순)
        return topProductIds.stream()
            .map(id -> products.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 실시간 인기 상품 조회 (Redis 기반)
     *
     * Use Case: UC-006 (실시간 버전)
     *
     * 집계 기준:
     * - Redis Sorted Set에 누적된 판매 수량 기준
     * - 실시간 주문 반영
     * - 상위 N개 조회
     *
     * @param topN 조회할 상위 개수 (기본 5개)
     * @return 실시간 인기 상품 목록
     */
    public List<Product> getRealtimePopularProducts(int topN) {
        log.info("[UC-006] 실시간 인기 상품 조회 (Redis) - TOP {}", topN);

        try {
            // 1. Redis에서 인기 상품 ID 조회 (Sorted Set - 판매 수량 기준)
            List<Long> productIds = productRedisRepository.getTopPopularProductIds(topN);

            if (productIds.isEmpty()) {
                log.info("[UC-006] Redis 인기상품 데이터 없음 - 기본 목록 반환");
                // Redis에 데이터가 없으면 기본 목록 반환
                return productRepository.findAvailableProducts(
                    org.springframework.data.domain.PageRequest.of(0, topN)
                ).getContent();
            }

            // 2. 상품 정보 조회 (DB 또는 Redis 캐시)
            List<Product> products = productRepository.findAllById(productIds);

            // 3. 원래 순서 유지 (인기도 순)
            List<Product> sortedProducts = productIds.stream()
                .map(id -> products.stream()
                    .filter(p -> p.getId().equals(id))
                    .findFirst()
                    .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            log.info("[UC-006] 실시간 인기 상품 조회 완료 - {} 개 반환", sortedProducts.size());
            return sortedProducts;

        } catch (Exception e) {
            log.error("[UC-006] 실시간 인기 상품 조회 실패 - 기본 목록 반환", e);
            // Redis 장애 시 기본 목록 반환
            return productRepository.findAvailableProducts(
                org.springframework.data.domain.PageRequest.of(0, topN)
            ).getContent();
        }
    }

    /**
     * 실시간 인기 상품 조회 (상세 정보 포함)
     *
     * Use Case: UC-006 (실시간 버전 + 판매 수량 포함)
     *
     * @param topN 조회할 상위 개수
     * @return 실시간 인기 상품 목록 (판매 수량 포함)
     */
    public List<PopularProductInfo> getRealtimePopularProductsWithStats(int topN) {
        log.info("[UC-006] 실시간 인기 상품 조회 (통계 포함) - TOP {}", topN);

        try {
            // 1. Redis에서 인기 상품 ID와 스코어 조회
            List<com.hhplus.ecommerce.product.infrastructure.persistence.ProductRedisRepository.PopularProduct> popularProducts
                = productRedisRepository.getTopPopularProducts(topN);

            if (popularProducts.isEmpty()) {
                log.info("[UC-006] Redis 인기상품 데이터 없음");
                return List.of();
            }

            // 2. 상품 ID 추출
            List<Long> productIds = popularProducts.stream()
                .map(com.hhplus.ecommerce.product.infrastructure.persistence.ProductRedisRepository.PopularProduct::getProductId)
                .toList();

            // 3. 상품 정보 조회
            List<Product> products = productRepository.findAllById(productIds);

            // 4. 상품 정보 + 판매 수량 매핑
            List<PopularProductInfo> result = popularProducts.stream()
                .map(pp -> {
                    Product product = products.stream()
                        .filter(p -> p.getId().equals(pp.getProductId()))
                        .findFirst()
                        .orElse(null);

                    if (product == null) {
                        return null;
                    }

                    return PopularProductInfo.builder()
                        .product(product)
                        .salesCount(pp.getSalesCount())
                        .rank(popularProducts.indexOf(pp) + 1L)
                        .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            log.info("[UC-006] 실시간 인기 상품 조회 완료 (통계 포함) - {} 개 반환", result.size());
            return result;

        } catch (Exception e) {
            log.error("[UC-006] 실시간 인기 상품 조회 실패 (통계 포함)", e);
            return List.of();
        }
    }

    /**
     * 인기 상품 정보 DTO (상품 + 판매 통계)
     */
    @lombok.Builder
    @lombok.Getter
    public static class PopularProductInfo {
        private Product product;      // 상품 정보
        private Long salesCount;      // 판매 수량
        private Long rank;            // 순위
    }
}

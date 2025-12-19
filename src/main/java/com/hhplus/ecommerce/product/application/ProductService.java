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

    private static final String CACHE_NAME_PRODUCT_INFO = "product-info";
    private static final String CACHE_KEY_POPULAR_PRODUCTS_TOP5 = "cache:popular:products:top5";
    private static final long CACHE_TTL_POPULAR_PRODUCTS_TOP5_MINUTES = 10L;
    private static final String LOCK_KEY_POPULAR_PRODUCTS_REFRESH = "lock:popular:products:refresh";

    /**
     * 상품 목록 조회 (UC-003)
     */
    public Page<Product> getAvailableProducts(Pageable pageable) {
        log.info("[UC-003] 상품 목록 조회 - page: {}, size: {}",
                 pageable.getPageNumber(), pageable.getPageSize());

        return productRepository.findAvailableProducts(pageable);
    }

    /**
     * 카테고리별 상품 목록 조회 (UC-003)
     */
    public Page<Product> getProductsByCategory(Long categoryId, Pageable pageable) {
        log.info("[UC-003] 카테고리별 상품 조회 - categoryId: {}", categoryId);

        return productRepository.findByCategoryId(categoryId, pageable);
    }

    /**
     * 상품 상세 조회 (UC-004)
     */
    @Cacheable(value = CACHE_NAME_PRODUCT_INFO, key = "#productId")
    public Product getProduct(Long productId) {
        log.info("[UC-004] DB에서 상품 조회 - productId: {}", productId);

        return productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));
    }

    /**
     * 인기 상품 조회 (UC-006)
     * 최근 3일 판매량 기준 TOP 5, Redis 캐시 사용 (TTL: 10분)
     */
    public List<Product> getPopularProducts() {
        log.info("[UC-006] 인기 상품 조회 - 캐시 확인");

        List<Product> cachedProducts = getCachedPopularProducts();
        if (cachedProducts != null) {
            log.info("[UC-006] 캐시 히트 - {} 개 상품 반환", cachedProducts.size());
            return cachedProducts;
        }

        log.info("[UC-006] 캐시 미스 - 분산락으로 캐시 갱신 시도");
        return refreshPopularProductsCache();
    }

    /**
     * 인기 상품 캐시 갱신 (Redisson 분산락 + Double-Check Pattern)
     */
    public List<Product> refreshPopularProductsCache() {
        RLock lock = redissonClient.getLock(LOCK_KEY_POPULAR_PRODUCTS_REFRESH);

        try {
            boolean isLocked = lock.tryLock(5, 15, TimeUnit.SECONDS);

            if (!isLocked) {
                log.warn("[UC-006] 분산락 획득 실패 - 다른 서버가 캐시 갱신 중");
                throw new IllegalStateException("인기 상품 캐시 갱신 중입니다. 잠시 후 다시 시도해주세요.");
            }

            log.info("[UC-006] 캐시 갱신 시작 - Redisson 분산락 획득 완료");

            List<Product> cachedProducts = getCachedPopularProducts();
            if (cachedProducts != null) {
                log.info("[UC-006] Double-Check: 이미 다른 서버가 캐시 갱신 완료");
                return cachedProducts;
            }

            log.info("[UC-006] DB 집계 시작");
            List<Product> products = fetchPopularProductsFromDBWithTransaction();

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
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("[UC-006] Redisson 분산락 해제 완료");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Product> fetchPopularProductsFromDBWithTransaction() {
        return fetchPopularProductsFromDB();
    }

    @SuppressWarnings("unchecked")
    private List<Product> getCachedPopularProducts() {
        Object cached = redisTemplate.opsForValue().get(CACHE_KEY_POPULAR_PRODUCTS_TOP5);
        return cached != null ? (List<Product>) cached : null;
    }

    private List<Product> fetchPopularProductsFromDB() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(2);

        List<Long> topProductIds = productStatisticsRepository
            .findTopProductIdsByDateRange(startDate, endDate, 5);

        if (topProductIds.isEmpty()) {
            log.info("[UC-006] 통계 데이터 없음, 기본 목록 반환");
            return productRepository.findAvailableProducts(
                org.springframework.data.domain.PageRequest.of(0, 5)
            ).getContent();
        }

        List<Product> products = productRepository.findAllById(topProductIds);

        return topProductIds.stream()
            .map(id -> products.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElse(null))
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * 실시간 인기 상품 조회 (UC-006 - Redis 기반)
     */
    public List<Product> getRealtimePopularProducts(int topN) {
        log.info("[UC-006] 실시간 인기 상품 조회 (Redis) - TOP {}", topN);

        try {
            List<Long> productIds = productRedisRepository.getTopPopularProductIds(topN);

            if (productIds.isEmpty()) {
                log.info("[UC-006] Redis 인기상품 데이터 없음 - 기본 목록 반환");
                return productRepository.findAvailableProducts(
                    org.springframework.data.domain.PageRequest.of(0, topN)
                ).getContent();
            }

            List<Product> products = productRepository.findAllById(productIds);

            List<Product> sortedProducts = productIds.stream()
                .map(id -> products.stream()
                    .filter(p -> p.getId().equals(id))
                    .findFirst()
                    .orElse(null))
                .filter(Objects::nonNull)
                .toList();

            log.info("[UC-006] 실시간 인기 상품 조회 완료 - {} 개 반환", sortedProducts.size());
            return sortedProducts;

        } catch (Exception e) {
            log.error("[UC-006] 실시간 인기 상품 조회 실패 - 기본 목록 반환", e);
            return productRepository.findAvailableProducts(
                org.springframework.data.domain.PageRequest.of(0, topN)
            ).getContent();
        }
    }

    /**
     * 실시간 인기 상품 조회 (UC-006 - 통계 포함)
     */
    public List<PopularProductInfo> getRealtimePopularProductsWithStats(int topN) {
        log.info("[UC-006] 실시간 인기 상품 조회 (통계 포함) - TOP {}", topN);

        try {
            List<com.hhplus.ecommerce.product.infrastructure.persistence.ProductRedisRepository.PopularProduct> popularProducts
                = productRedisRepository.getTopPopularProducts(topN);

            if (popularProducts.isEmpty()) {
                log.info("[UC-006] Redis 인기상품 데이터 없음");
                return List.of();
            }

            List<Long> productIds = popularProducts.stream()
                .map(com.hhplus.ecommerce.product.infrastructure.persistence.ProductRedisRepository.PopularProduct::getProductId)
                .toList();

            List<Product> products = productRepository.findAllById(productIds);

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
                .toList();

            log.info("[UC-006] 실시간 인기 상품 조회 완료 (통계 포함) - {} 개 반환", result.size());
            return result;

        } catch (Exception e) {
            log.error("[UC-006] 실시간 인기 상품 조회 실패 (통계 포함)", e);
            return List.of();
        }
    }

    @lombok.Builder
    @lombok.Getter
    public static class PopularProductInfo {
        private Product product;
        private Long salesCount;
        private Long rank;
    }
}

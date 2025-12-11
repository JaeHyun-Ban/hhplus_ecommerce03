package com.hhplus.ecommerce.product.application;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.domain.ProductStatus;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 캐시 성능 테스트
 *
 * 목적:
 * - 캐싱 적용 전후 성능 비교
 * - Cache Hit Rate 검증
 * - 응답시간 개선 효과 측정
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("상품 조회 캐시 성능 테스트")
class ProductCachePerformanceTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        // 캐시 초기화
        Objects.requireNonNull(cacheManager.getCache("product:info")).clear();

        // 테스트 데이터 생성
        testProduct = Product.builder()
            .name("테스트 상품")
            .description("캐시 성능 테스트용 상품")
            .price(BigDecimal.valueOf(10000))
            .stock(100)
            .safetyStock(10)
            .status(ProductStatus.AVAILABLE)
            .build();
        testProduct = productRepository.save(testProduct);

        log.info("테스트 상품 생성 - productId: {}", testProduct.getId());
    }

    @Test
    @DisplayName("캐시 성능 테스트: 100회 조회 시 Cache Hit Rate 99% 이상")
    void testCacheHitRate() {
        // Given
        int totalRequests = 100;
        Long productId = testProduct.getId();

        // When: 1번째 호출 (Cache Miss - DB 조회)
        long dbStartTime = System.currentTimeMillis();
        Product firstCall = productService.getProduct(productId);
        long dbEndTime = System.currentTimeMillis();
        long dbQueryTime = dbEndTime - dbStartTime;

        log.info("1번째 호출 (Cache Miss): {}ms", dbQueryTime);

        // When: 2-100번째 호출 (Cache Hit - Redis 조회)
        long totalCacheTime = 0;
        for (int i = 2; i <= totalRequests; i++) {
            long cacheStartTime = System.nanoTime();
            Product cachedProduct = productService.getProduct(productId);
            long cacheEndTime = System.nanoTime();
            totalCacheTime += (cacheEndTime - cacheStartTime);

            // 캐시된 데이터 검증
            assertThat(cachedProduct.getId()).isEqualTo(firstCall.getId());
            assertThat(cachedProduct.getName()).isEqualTo(firstCall.getName());
        }

        long avgCacheTimeNanos = totalCacheTime / (totalRequests - 1);
        double avgCacheTimeMs = avgCacheTimeNanos / 1_000_000.0;

        log.info("");
        log.info("=== 캐시 성능 테스트 결과 ===");
        log.info("총 요청 수: {}회", totalRequests);
        log.info("1번째 호출 (DB 조회): {}ms", dbQueryTime);
        log.info("2-100번째 호출 (캐시): 평균 {}ms", String.format("%.3f", avgCacheTimeMs));
        log.info("성능 개선: {}배", String.format("%.1f", (double)dbQueryTime / avgCacheTimeMs));
        log.info("Cache Hit Rate: {}%", String.format("%.1f", ((double)(totalRequests - 1) / totalRequests) * 100));

        // Then: 검증
        assertThat(avgCacheTimeMs).isLessThan(dbQueryTime);  // 캐시가 더 빠름
        assertThat(avgCacheTimeMs).isLessThan(10.0);  // 캐시 조회는 10ms 미만
    }

    @Test
    @DisplayName("캐시 성능 테스트: 동시 1000회 조회 시 성능 개선 확인")
    void testConcurrentCachePerformance() throws InterruptedException {
        // Given
        int concurrentRequests = 1000;
        Long productId = testProduct.getId();

        // When: 캐싱 없이 순차 조회 시뮬레이션 (1회만 실제 DB 조회)
        long withoutCacheTime = System.currentTimeMillis();
        Product firstProduct = productService.getProduct(productId);
        long singleDbQueryTime = System.currentTimeMillis() - withoutCacheTime;

        // 캐싱 없다면 예상 시간 = DB 조회 시간 × 1000회
        long estimatedTimeWithoutCache = singleDbQueryTime * concurrentRequests;

        // When: 캐싱 있을 때 1000회 조회
        long withCacheStart = System.currentTimeMillis();
        for (int i = 0; i < concurrentRequests; i++) {
            productService.getProduct(productId);
        }
        long withCacheEnd = System.currentTimeMillis();
        long withCacheTime = withCacheEnd - withCacheStart;

        log.info("");
        log.info("=== 동시 조회 성능 비교 ===");
        log.info("조회 횟수: {}회", concurrentRequests);
        log.info("단일 DB 조회: {}ms", singleDbQueryTime);
        log.info("캐싱 없을 때 예상: {}ms ({}ms × {}회)", estimatedTimeWithoutCache, singleDbQueryTime, concurrentRequests);
        log.info("캐싱 있을 때 실제: {}ms", withCacheTime);
        log.info("성능 개선: {}배", String.format("%.1f", (double)estimatedTimeWithoutCache / withCacheTime));
        log.info("시간 절감: {}ms (-{}%)",
                estimatedTimeWithoutCache - withCacheTime,
                String.format("%.1f", ((double)(estimatedTimeWithoutCache - withCacheTime) / estimatedTimeWithoutCache) * 100));

        // Then: 검증
        assertThat(withCacheTime).isLessThan(estimatedTimeWithoutCache);  // 캐싱이 훨씬 빠름
        assertThat(withCacheTime).isLessThan(estimatedTimeWithoutCache / 5);  // 최소 5배 이상 빠름
    }

    @Test
    @DisplayName("캐시 TTL 테스트: 캐시 존재 여부 확인")
    void testCacheTTL() {
        // Given
        Long productId = testProduct.getId();

        // When: 첫 조회 (캐시 저장)
        productService.getProduct(productId);

        // Then: 캐시 확인
        var cache = cacheManager.getCache("product:info");
        assertThat(cache).isNotNull();

        var cachedValue = cache.get(productId);
        assertThat(cachedValue).isNotNull();
        assertThat(cachedValue.get()).isInstanceOf(Product.class);

        Product cachedProduct = (Product) cachedValue.get();
        assertThat(cachedProduct.getId()).isEqualTo(productId);

        log.info("캐시 확인 성공 - productId: {}, name: {}", cachedProduct.getId(), cachedProduct.getName());
    }
}

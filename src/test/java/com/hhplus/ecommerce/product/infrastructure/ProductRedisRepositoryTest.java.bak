package com.hhplus.ecommerce.product.infrastructure;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.product.domain.Category;
import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.domain.ProductStatus;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRedisRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * ProductRedisRepository Sorted Set + Hash 단위 테스트
 *
 * 테스트 대상:
 * - 인기상품 순위 관리 (Sorted Set)
 * - 상품 정보 캐싱 (Hash)
 */
@Slf4j
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("ProductRedisRepository 단위 테스트")
class ProductRedisRepositoryTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    static {
        redis.start();
        System.setProperty("spring.data.redis.host", redis.getHost());
        System.setProperty("spring.data.redis.port", redis.getMappedPort(6379).toString());
    }

    @Autowired
    private ProductRedisRepository productRedisRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        // Redis 초기화
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("성공: 인기도 스코어 증가 - ZINCRBY")
    void incrementPopularityScore_Success() {
        // Given
        Long productId = 1L;
        Integer quantity = 5;

        // When
        productRedisRepository.incrementPopularityScore(productId, quantity);

        // Then
        Long score = productRedisRepository.getProductScore(productId);
        assertThat(score).isEqualTo(5L);

        // 다시 증가
        productRedisRepository.incrementPopularityScore(productId, 3);
        score = productRedisRepository.getProductScore(productId);
        assertThat(score).isEqualTo(8L);

        log.info("인기도 스코어 증가 확인 - productId: {}, score: {}", productId, score);
    }

    @Test
    @DisplayName("성공: 상품 정보 캐시 저장 및 조회 - Hash")
    void cacheProductInfo_Success() {
        // Given
        Category category = Category.builder()
            .id(1L)
            .name("Electronics")
            .build();

        Product product = Product.builder()
            .id(100L)
            .name("Samsung Galaxy S24")
            .description("Latest flagship smartphone")
            .price(new BigDecimal("1200000"))
            .stock(50)
            .category(category)
            .status(ProductStatus.AVAILABLE)
            .build();

        // When
        productRedisRepository.cacheProductInfo(product);

        // Then
        Map<String, String> cached = productRedisRepository.getCachedProductInfo(100L);
        assertThat(cached).isNotNull();
        assertThat(cached.get("id")).isEqualTo("100");
        assertThat(cached.get("name")).isEqualTo("Samsung Galaxy S24");
        assertThat(cached.get("description")).isEqualTo("Latest flagship smartphone");
        assertThat(cached.get("price")).isEqualTo("1200000");
        assertThat(cached.get("stock")).isEqualTo("50");
        assertThat(cached.get("categoryId")).isEqualTo("1");
        assertThat(cached.get("status")).isEqualTo("AVAILABLE");

        log.info("상품 정보 캐시 확인 - cached: {}", cached);
    }

    @Test
    @DisplayName("성공: TOP N 인기상품 조회 - 순위 확인")
    void getTopPopularProducts_Success() {
        // Given - 5개 상품에 다른 판매량 설정
        productRedisRepository.incrementPopularityScore(1L, 100); // 1위
        productRedisRepository.incrementPopularityScore(2L, 80);  // 2위
        productRedisRepository.incrementPopularityScore(3L, 60);  // 3위
        productRedisRepository.incrementPopularityScore(4L, 40);  // 4위
        productRedisRepository.incrementPopularityScore(5L, 20);  // 5위

        // When - TOP 3 조회
        List<ProductRedisRepository.PopularProduct> top3 = productRedisRepository.getTopPopularProducts(3);

        // Then
        assertThat(top3).hasSize(3);
        assertThat(top3.get(0).getProductId()).isEqualTo(1L);
        assertThat(top3.get(0).getSalesCount()).isEqualTo(100L);
        assertThat(top3.get(1).getProductId()).isEqualTo(2L);
        assertThat(top3.get(1).getSalesCount()).isEqualTo(80L);
        assertThat(top3.get(2).getProductId()).isEqualTo(3L);
        assertThat(top3.get(2).getSalesCount()).isEqualTo(60L);

        log.info("TOP 3 인기상품 - {}", top3);
    }

    @Test
    @DisplayName("성공: 특정 상품의 순위 조회 - ZREVRANK")
    void getProductRank_Success() {
        // Given
        productRedisRepository.incrementPopularityScore(1L, 100);
        productRedisRepository.incrementPopularityScore(2L, 80);
        productRedisRepository.incrementPopularityScore(3L, 60);

        // When
        Long rank1 = productRedisRepository.getProductRank(1L); // 1위
        Long rank2 = productRedisRepository.getProductRank(2L); // 2위
        Long rank3 = productRedisRepository.getProductRank(3L); // 3위

        // Then
        assertThat(rank1).isEqualTo(1L);
        assertThat(rank2).isEqualTo(2L);
        assertThat(rank3).isEqualTo(3L);

        log.info("순위 확인 - product 1: {}위, product 2: {}위, product 3: {}위", rank1, rank2, rank3);
    }

    @Test
    @DisplayName("성공: 인기상품 통계 조회")
    void getStats_Success() {
        // Given
        productRedisRepository.incrementPopularityScore(1L, 100);
        productRedisRepository.incrementPopularityScore(2L, 80);
        productRedisRepository.incrementPopularityScore(3L, 60);

        // When
        ProductRedisRepository.PopularProductStats stats = productRedisRepository.getStats();

        // Then
        assertThat(stats.getTotalProducts()).isEqualTo(3L);
        assertThat(stats.getTotalSales()).isEqualTo(240L); // 100 + 80 + 60

        log.info("통계 확인 - totalProducts: {}, totalSales: {}",
                stats.getTotalProducts(), stats.getTotalSales());
    }

    @Test
    @DisplayName("성공: 인기상품 데이터 초기화")
    void resetPopularProducts_Success() {
        // Given
        productRedisRepository.incrementPopularityScore(1L, 100);
        productRedisRepository.incrementPopularityScore(2L, 80);

        // When
        productRedisRepository.resetPopularProducts();

        // Then
        List<Long> topProducts = productRedisRepository.getTopPopularProductIds(10);
        assertThat(topProducts).isEmpty();

        ProductRedisRepository.PopularProductStats stats = productRedisRepository.getStats();
        assertThat(stats.getTotalProducts()).isEqualTo(0L);
        assertThat(stats.getTotalSales()).isEqualTo(0L);

        log.info("초기화 완료");
    }

    @Test
    @DisplayName("성공: 상품 정보 캐시 삭제")
    void evictProductCache_Success() {
        // Given
        Product product = Product.builder()
            .id(100L)
            .name("Test Product")
            .description("Test")
            .price(new BigDecimal("10000"))
            .stock(10)
            .status(ProductStatus.AVAILABLE)
            .build();

        productRedisRepository.cacheProductInfo(product);

        // 캐시 존재 확인
        Map<String, String> cached = productRedisRepository.getCachedProductInfo(100L);
        assertThat(cached).isNotNull();

        // When
        productRedisRepository.evictProductCache(100L);

        // Then
        cached = productRedisRepository.getCachedProductInfo(100L);
        assertThat(cached).isNull();

        log.info("캐시 삭제 완료");
    }
}

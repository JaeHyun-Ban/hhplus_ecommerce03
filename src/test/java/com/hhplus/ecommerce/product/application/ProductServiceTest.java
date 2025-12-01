package com.hhplus.ecommerce.product.application;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.product.domain.Category;
import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.domain.ProductStatistics;
import com.hhplus.ecommerce.product.domain.ProductStatus;
import com.hhplus.ecommerce.product.infrastructure.persistence.CategoryRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductStatisticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * ProductService 통합 테스트 (TestContainers 사용)
 *
 * 테스트 전략:
 * - 실제 MySQL 컨테이너를 사용한 통합 테스트
 * - 상품 조회, 인기 상품 조회 등 실제 DB 기반 테스트
 * - Redis 컨테이너로 Redisson 분산락 테스트
 */
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("ProductService 통합 테스트 (TestContainers)")
class ProductServiceTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    static {
        redis.start();
        System.setProperty("spring.data.redis.host", redis.getHost());
        System.setProperty("spring.data.redis.port", redis.getMappedPort(6379).toString());
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductStatisticsRepository productStatisticsRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Category testCategory;

    @BeforeEach
    void setUp() {
        // Redis 캐시 초기화
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        // 테스트 데이터 초기화
        productStatisticsRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        // 테스트용 카테고리 생성
        testCategory = createAndSaveCategory("전자제품", "전자제품 카테고리");
    }

    @Nested
    @DisplayName("상품 목록 조회 테스트")
    class GetAvailableProductsTest {

        @Test
        @DisplayName("성공: 판매 가능한 상품 목록 조회")
        void getAvailableProducts_Success() {
            // Given
            createAndSaveProduct("노트북", 50, BigDecimal.valueOf(1500000), ProductStatus.AVAILABLE);
            createAndSaveProduct("마우스", 100, BigDecimal.valueOf(50000), ProductStatus.AVAILABLE);
            createAndSaveProduct("키보드", 80, BigDecimal.valueOf(80000), ProductStatus.AVAILABLE);

            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Product> result = productService.getAvailableProducts(pageable);

            // Then
            assertThat(result.getContent()).hasSize(3);
            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getContent()).extracting(Product::getName)
                    .contains("노트북", "마우스", "키보드");
        }

        @Test
        @DisplayName("성공: 상품이 없는 경우 빈 페이지 반환")
        void getAvailableProducts_Empty() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Product> result = productService.getAvailableProducts(pageable);

            // Then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("성공: OUT_OF_STOCK 상품은 제외")
        void getAvailableProducts_ExcludeOutOfStock() {
            // Given
            createAndSaveProduct("노트북", 50, BigDecimal.valueOf(1500000), ProductStatus.AVAILABLE);
            createAndSaveProduct("품절상품", 0, BigDecimal.valueOf(100000), ProductStatus.OUT_OF_STOCK);
            createAndSaveProduct("마우스", 100, BigDecimal.valueOf(50000), ProductStatus.AVAILABLE);

            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Product> result = productService.getAvailableProducts(pageable);

            // Then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent()).extracting(Product::getName)
                    .containsExactlyInAnyOrder("노트북", "마우스");
        }

        @Test
        @DisplayName("성공: 페이징 처리 확인")
        void getAvailableProducts_Pagination() {
            // Given
            for (int i = 1; i <= 10; i++) {
                createAndSaveProduct("상품" + i, 100, BigDecimal.valueOf(10000 * i), ProductStatus.AVAILABLE);
            }

            Pageable firstPage = PageRequest.of(0, 3);
            Pageable secondPage = PageRequest.of(1, 3);

            // When
            Page<Product> firstResult = productService.getAvailableProducts(firstPage);
            Page<Product> secondResult = productService.getAvailableProducts(secondPage);

            // Then
            assertThat(firstResult.getContent()).hasSize(3);
            assertThat(secondResult.getContent()).hasSize(3);
            assertThat(firstResult.getTotalElements()).isEqualTo(10);
            assertThat(secondResult.getTotalElements()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("카테고리별 상품 목록 조회 테스트")
    class GetProductsByCategoryTest {

        @Test
        @DisplayName("성공: 특정 카테고리의 상품 목록 조회")
        void getProductsByCategory_Success() {
            // Given
            Category electronicsCategory = testCategory;
            Category furnitureCategory = createAndSaveCategory("가구", "가구 카테고리");

            createAndSaveProduct("노트북", 50, BigDecimal.valueOf(1500000), ProductStatus.AVAILABLE, electronicsCategory);
            createAndSaveProduct("마우스", 100, BigDecimal.valueOf(50000), ProductStatus.AVAILABLE, electronicsCategory);
            createAndSaveProduct("책상", 20, BigDecimal.valueOf(200000), ProductStatus.AVAILABLE, furnitureCategory);

            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Product> result = productService.getProductsByCategory(electronicsCategory.getId(), pageable);

            // Then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent()).extracting(Product::getName)
                    .containsExactlyInAnyOrder("노트북", "마우스");
            assertThat(result.getContent()).allMatch(
                    p -> p.getCategory().getId().equals(electronicsCategory.getId())
            );
        }

        @Test
        @DisplayName("성공: 카테고리에 상품이 없는 경우")
        void getProductsByCategory_Empty() {
            // Given
            Category emptyCategory = createAndSaveCategory("빈카테고리", "상품이 없는 카테고리");
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Product> result = productService.getProductsByCategory(emptyCategory.getId(), pageable);

            // Then
            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("상품 상세 조회 테스트")
    class GetProductTest {

        @Test
        @DisplayName("성공: 상품 상세 정보 조회")
        void getProduct_Success() {
            // Given
            Product saved = createAndSaveProduct("노트북", 50, BigDecimal.valueOf(1500000), ProductStatus.AVAILABLE);

            // When
            Product result = productService.getProduct(saved.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(saved.getId());
            assertThat(result.getName()).isEqualTo("노트북");
            assertThat(result.getStock()).isEqualTo(50);
            assertThat(result.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(1500000));
            assertThat(result.getStatus()).isEqualTo(ProductStatus.AVAILABLE);
        }

        @Test
        @DisplayName("실패: 상품을 찾을 수 없음")
        void getProduct_NotFound() {
            // Given
            Long nonExistentId = 999L;

            // When & Then
            assertThatThrownBy(() -> productService.getProduct(nonExistentId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("상품을 찾을 수 없습니다");
        }

    }

    @Nested
    @DisplayName("인기 상품 조회 테스트")
    class GetPopularProductsTest {

        @Test
        @DisplayName("성공: 최근 3일 판매량 기준 TOP 5 조회")
        void getPopularProducts_Success() {
            // Given
            Product product1 = createAndSaveProduct("노트북", 50, BigDecimal.valueOf(1500000), ProductStatus.AVAILABLE);
            Product product2 = createAndSaveProduct("마우스", 100, BigDecimal.valueOf(50000), ProductStatus.AVAILABLE);
            Product product3 = createAndSaveProduct("키보드", 80, BigDecimal.valueOf(80000), ProductStatus.AVAILABLE);

            LocalDate today = LocalDate.now();

            // 통계 데이터 생성 (판매량: 키보드 > 노트북 > 마우스)
            createAndSaveStatistics(product3, today, 100, BigDecimal.valueOf(8000000)); // 키보드 1위
            createAndSaveStatistics(product1, today, 80, BigDecimal.valueOf(120000000));  // 노트북 2위
            createAndSaveStatistics(product2, today, 50, BigDecimal.valueOf(2500000));  // 마우스 3위

            // When
            List<Product> result = productService.getPopularProducts();

            // Then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).getId()).isEqualTo(product3.getId()); // 키보드
            assertThat(result.get(1).getId()).isEqualTo(product1.getId()); // 노트북
            assertThat(result.get(2).getId()).isEqualTo(product2.getId()); // 마우스
        }

        @Test
        @DisplayName("성공: 통계 데이터 없을 때 최신 상품 5개 반환")
        void getPopularProducts_NoStatistics() {
            // Given
            createAndSaveProduct("노트북", 50, BigDecimal.valueOf(1500000), ProductStatus.AVAILABLE);
            createAndSaveProduct("마우스", 100, BigDecimal.valueOf(50000), ProductStatus.AVAILABLE);

            // When
            List<Product> result = productService.getPopularProducts();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Product::getName)
                    .contains("노트북", "마우스");
        }

        @Test
        @DisplayName("성공: 인기 상품이 5개 미만일 때")
        void getPopularProducts_LessThan5() {
            // Given
            Product product1 = createAndSaveProduct("노트북", 50, BigDecimal.valueOf(1500000), ProductStatus.AVAILABLE);
            Product product2 = createAndSaveProduct("마우스", 100, BigDecimal.valueOf(50000), ProductStatus.AVAILABLE);

            LocalDate today = LocalDate.now();
            createAndSaveStatistics(product1, today, 80, BigDecimal.valueOf(120000000));
            createAndSaveStatistics(product2, today, 50, BigDecimal.valueOf(2500000));

            // When
            List<Product> result = productService.getPopularProducts();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(product1.getId());
            assertThat(result.get(1).getId()).isEqualTo(product2.getId());
        }

    }

    @Nested
    @DisplayName("상품 재고 관련 테스트")
    class ProductStockTest {

        @Test
        @DisplayName("성공: 재고 있는 상품만 조회")
        void getAvailableProducts_OnlyWithStock() {
            // Given
            createAndSaveProduct("재고있음", 50, BigDecimal.valueOf(100000), ProductStatus.AVAILABLE);
            createAndSaveProduct("재고없음", 0, BigDecimal.valueOf(100000), ProductStatus.OUT_OF_STOCK);

            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Product> result = productService.getAvailableProducts(pageable);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("재고있음");
            assertThat(result.getContent().get(0).getStock()).isGreaterThan(0);
        }
    }

    // ========================================
    // 테스트 데이터 생성 헬퍼 메서드
    // ========================================

    private Category createAndSaveCategory(String name, String description) {
        Category category = Category.builder()
                .name(name)
                .description(description)
                .createdAt(LocalDateTime.now())
                .build();
        return categoryRepository.save(category);
    }

    private Product createAndSaveProduct(String name, int stock, BigDecimal price, ProductStatus status) {
        return createAndSaveProduct(name, stock, price, status, testCategory);
    }

    private Product createAndSaveProduct(String name, int stock, BigDecimal price, ProductStatus status, Category category) {
        Product product = Product.builder()
                .name(name)
                .description(name + " 상세 설명")
                .price(price)
                .stock(stock)
                .safetyStock(10)
                .category(category)
                .status(status)
                .version(0L)
                .build();
        return productRepository.save(product);
    }

    private void createAndSaveStatistics(Product product, LocalDate date, int salesCount, BigDecimal salesAmount) {
        ProductStatistics stats = ProductStatistics.builder()
                .product(product)
                .statisticsDate(date)
                .salesCount(salesCount)
                .salesAmount(salesAmount)
                .viewCount(0)
                .build();
        productStatisticsRepository.save(stats);
    }
}

package com.hhplus.ecommerce.application.product;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.domain.product.*;
import com.hhplus.ecommerce.infrastructure.persistence.product.CategoryRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.ProductRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.ProductStatisticsRepository;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * ProductService 통합 테스트 (TestContainers 사용)
 *
 * 테스트 전략:
 * - 실제 MySQL 컨테이너를 사용한 통합 테스트
 * - JPA, 트랜잭션, DB 제약조건 등 실제 동작 검증
 * - 페이징, 통계 조회 등 복잡한 쿼리 테스트
 * - Redis 컨테이너로 Redisson 분산락 테스트
 */
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("ProductService 통합 테스트 (TestContainers)")
class ProductServiceIntegrationTest {

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
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductStatisticsRepository productStatisticsRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Category testCategory;

    @BeforeEach
    void setUp() {
        // Redis 캐시 초기화
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        // 각 테스트 전에 DB 초기화
        productStatisticsRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        // 테스트용 카테고리 생성 (이름을 timestamp로 유니크하게)
        String uniqueName = "테스트카테고리_" + System.currentTimeMillis();
        testCategory = categoryRepository.save(Category.builder()
                .name(uniqueName)
                .description("통합 테스트용 카테고리")
                .build());
    }

    @Nested
    @DisplayName("UC-003: 상품 목록 조회 통합 테스트")
    class GetAvailableProductsIntegrationTest {

        @Test
        @DisplayName("성공: 판매 가능한 상품 목록 조회 (페이징)")
        void getAvailableProducts_Success() {
            // Given - 10개의 상품 생성 (5개는 판매 가능, 5개는 품절)
            for (int i = 1; i <= 5; i++) {
                productRepository.save(Product.builder()
                        .name("판매가능상품" + i)
                        .description("재고 있음")
                        .price(BigDecimal.valueOf(10000 * i))
                        .stock(100)
                        .safetyStock(10)
                        .category(testCategory)
                        .status(ProductStatus.AVAILABLE)
                        .build());
            }

            for (int i = 1; i <= 5; i++) {
                productRepository.save(Product.builder()
                        .name("품절상품" + i)
                        .description("재고 없음")
                        .price(BigDecimal.valueOf(10000 * i))
                        .stock(0)
                        .safetyStock(10)
                        .category(testCategory)
                        .status(ProductStatus.OUT_OF_STOCK)
                        .build());
            }

            // When - 페이지 크기 3으로 조회
            Pageable pageable = PageRequest.of(0, 3);
            Page<Product> result = productService.getAvailableProducts(pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(3);
            assertThat(result.getTotalElements()).isEqualTo(5); // 판매 가능 상품만
            assertThat(result.getTotalPages()).isEqualTo(2); // 5개 / 3 = 2페이지

            // 모든 상품이 판매 가능 상태
            result.getContent().forEach(product -> {
                assertThat(product.getStatus()).isEqualTo(ProductStatus.AVAILABLE);
                assertThat(product.getStock()).isGreaterThan(0);
            });
        }

        @Test
        @DisplayName("성공: 페이징 - 두 번째 페이지 조회")
        void getAvailableProducts_SecondPage() {
            // Given - 7개의 판매 가능 상품 생성
            for (int i = 1; i <= 7; i++) {
                productRepository.save(Product.builder()
                        .name("상품" + i)
                        .price(BigDecimal.valueOf(10000))
                        .stock(100)
                        .safetyStock(10)
                        .category(testCategory)
                        .status(ProductStatus.AVAILABLE)
                        .build());
            }

            // When - 두 번째 페이지 조회 (페이지 크기 3)
            Pageable pageable = PageRequest.of(1, 3);
            Page<Product> result = productService.getAvailableProducts(pageable);

            // Then
            assertThat(result.getContent()).hasSize(3);
            assertThat(result.getTotalElements()).isEqualTo(7);
            assertThat(result.getNumber()).isEqualTo(1); // 페이지 번호 확인
        }

        @Test
        @DisplayName("성공: 판매 가능 상품 없음")
        void getAvailableProducts_EmptyResult() {
            // Given - 품절 상품만 생성
            productRepository.save(Product.builder()
                    .name("품절상품")
                    .price(BigDecimal.valueOf(10000))
                    .stock(0)
                    .safetyStock(10)
                    .category(testCategory)
                    .status(ProductStatus.OUT_OF_STOCK)
                    .build());

            // When
            Pageable pageable = PageRequest.of(0, 10);
            Page<Product> result = productService.getAvailableProducts(pageable);

            // Then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("UC-003: 카테고리별 상품 조회 통합 테스트")
    class GetProductsByCategoryIntegrationTest {

        @Test
        @DisplayName("성공: 특정 카테고리의 상품 조회")
        void getProductsByCategory_Success() {
            // Given
            Category electronics = categoryRepository.save(Category.builder()
                    .name("전자제품")
                    .description("Electronics")
                    .build());

            Category clothing = categoryRepository.save(Category.builder()
                    .name("의류")
                    .description("Clothing")
                    .build());

            // 전자제품 3개
            for (int i = 1; i <= 3; i++) {
                productRepository.save(Product.builder()
                        .name("노트북" + i)
                        .price(BigDecimal.valueOf(1000000))
                        .stock(50)
                        .safetyStock(10)
                        .category(electronics)
                        .status(ProductStatus.AVAILABLE)
                        .build());
            }

            // 의류 2개
            for (int i = 1; i <= 2; i++) {
                productRepository.save(Product.builder()
                        .name("티셔츠" + i)
                        .price(BigDecimal.valueOf(50000))
                        .stock(100)
                        .safetyStock(20)
                        .category(clothing)
                        .status(ProductStatus.AVAILABLE)
                        .build());
            }

            // When - 전자제품 카테고리 조회
            Pageable pageable = PageRequest.of(0, 10);
            Page<Product> result = productService.getProductsByCategory(electronics.getId(), pageable);

            // Then
            assertThat(result.getTotalElements()).isEqualTo(3);
            result.getContent().forEach(product -> {
                assertThat(product.getCategory().getId()).isEqualTo(electronics.getId());
                assertThat(product.getName()).startsWith("노트북");
            });
        }

        @Test
        @DisplayName("성공: 카테고리에 상품 없음")
        void getProductsByCategory_EmptyCategory() {
            // Given - 빈 카테고리
            Category emptyCategory = categoryRepository.save(Category.builder()
                    .name("빈카테고리")
                    .build());

            // When
            Pageable pageable = PageRequest.of(0, 10);
            Page<Product> result = productService.getProductsByCategory(emptyCategory.getId(), pageable);

            // Then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("UC-004: 상품 상세 조회 통합 테스트")
    class GetProductIntegrationTest {

        @Test
        @DisplayName("성공: 상품 상세 조회")
        void getProduct_Success() {
            // Given
            Product saved = productRepository.save(Product.builder()
                    .name("맥북 프로")
                    .description("Apple MacBook Pro 16inch")
                    .price(BigDecimal.valueOf(3000000))
                    .stock(50)
                    .safetyStock(10)
                    .category(testCategory)
                    .status(ProductStatus.AVAILABLE)
                    .build());

            // When
            Product result = productService.getProduct(saved.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(saved.getId());
            assertThat(result.getName()).isEqualTo("맥북 프로");
            assertThat(result.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(3000000));
            assertThat(result.getStock()).isEqualTo(50);
            assertThat(result.getCategory().getId()).isEqualTo(testCategory.getId());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 상품 조회")
        void getProduct_NotFound() {
            // Given
            Long nonExistentId = 99999L;

            // When & Then
            assertThatThrownBy(() -> productService.getProduct(nonExistentId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("상품을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("성공: 품절 상품도 조회 가능")
        void getProduct_OutOfStock() {
            // Given
            Product outOfStock = productRepository.save(Product.builder()
                    .name("품절상품")
                    .price(BigDecimal.valueOf(10000))
                    .stock(0)
                    .safetyStock(10)
                    .category(testCategory)
                    .status(ProductStatus.OUT_OF_STOCK)
                    .build());

            // When
            Product result = productService.getProduct(outOfStock.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(ProductStatus.OUT_OF_STOCK);
            assertThat(result.getStock()).isZero();
        }
    }

    @Nested
    @DisplayName("UC-006: 인기 상품 조회 통합 테스트")
    class GetPopularProductsIntegrationTest {

        @Test
        @DisplayName("성공: 판매 통계 기반 인기 상품 조회")
        void getPopularProducts_WithStatistics() {
            // Given - 5개 상품 생성
            Product product1 = createProduct("상품1", 100);
            Product product2 = createProduct("상품2", 100);
            Product product3 = createProduct("상품3", 100);
            Product product4 = createProduct("상품4", 100);
            Product product5 = createProduct("상품5", 100);

            // 최근 3일 통계 생성 (판매량 순서: 3 > 1 > 5 > 2 > 4)
            LocalDate today = LocalDate.now();

            // 상품3: 총 300개 판매 (100 + 100 + 100)
            createStatistics(product3, today, 100, BigDecimal.valueOf(1000000));
            createStatistics(product3, today.minusDays(1), 100, BigDecimal.valueOf(1000000));
            createStatistics(product3, today.minusDays(2), 100, BigDecimal.valueOf(1000000));

            // 상품1: 총 200개 판매 (100 + 50 + 50)
            createStatistics(product1, today, 100, BigDecimal.valueOf(500000));
            createStatistics(product1, today.minusDays(1), 50, BigDecimal.valueOf(250000));
            createStatistics(product1, today.minusDays(2), 50, BigDecimal.valueOf(250000));

            // 상품5: 총 150개 판매
            createStatistics(product5, today, 150, BigDecimal.valueOf(750000));

            // 상품2: 총 100개 판매
            createStatistics(product2, today, 100, BigDecimal.valueOf(500000));

            // 상품4: 총 50개 판매
            createStatistics(product4, today.minusDays(1), 50, BigDecimal.valueOf(250000));

            // When
            List<Product> result = productService.getPopularProducts();

            // Then
            assertThat(result).hasSize(5);

            // 판매량 순서 확인: 상품3 > 상품1 > 상품5 > 상품2 > 상품4
            assertThat(result.get(0).getId()).isEqualTo(product3.getId());
            assertThat(result.get(1).getId()).isEqualTo(product1.getId());
            assertThat(result.get(2).getId()).isEqualTo(product5.getId());
            assertThat(result.get(3).getId()).isEqualTo(product2.getId());
            assertThat(result.get(4).getId()).isEqualTo(product4.getId());
        }

        @Test
        @DisplayName("성공: 통계 없을 때 최신 상품 5개 반환")
        void getPopularProducts_NoStatistics() {
            // Given - 7개 상품 생성 (통계 없음)
            for (int i = 1; i <= 7; i++) {
                createProduct("상품" + i, 100);
            }

            // When
            List<Product> result = productService.getPopularProducts();

            // Then
            assertThat(result).hasSize(5); // 최대 5개
            result.forEach(product -> {
                assertThat(product.getStatus()).isEqualTo(ProductStatus.AVAILABLE);
                assertThat(product.getStock()).isGreaterThan(0);
            });
        }

        @Test
        @DisplayName("성공: 인기 상품이 5개 미만")
        void getPopularProducts_LessThanFive() {
            // Given - 3개 상품 생성
            Product product1 = createProduct("상품1", 100);
            Product product2 = createProduct("상품2", 100);
            Product product3 = createProduct("상품3", 100);

            // 통계 생성
            LocalDate today = LocalDate.now();
            createStatistics(product1, today, 100, BigDecimal.valueOf(1000000));
            createStatistics(product2, today, 80, BigDecimal.valueOf(800000));
            createStatistics(product3, today, 60, BigDecimal.valueOf(600000));

            // When
            List<Product> result = productService.getPopularProducts();

            // Then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).getId()).isEqualTo(product1.getId());
            assertThat(result.get(1).getId()).isEqualTo(product2.getId());
            assertThat(result.get(2).getId()).isEqualTo(product3.getId());
        }

        @Test
        @DisplayName("성공: 최근 3일 범위만 집계")
        void getPopularProducts_OnlyLast3Days() {
            // Given
            Product recentProduct = createProduct("최근상품", 100);
            Product oldProduct = createProduct("예전상품", 100);

            LocalDate today = LocalDate.now();

            // 최근 상품: 오늘 판매
            createStatistics(recentProduct, today, 100, BigDecimal.valueOf(1000000));

            // 예전 상품: 4일 전 판매 (집계 범위 밖)
            createStatistics(oldProduct, today.minusDays(4), 200, BigDecimal.valueOf(2000000));

            // When
            List<Product> result = productService.getPopularProducts();

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(recentProduct.getId());
        }

        private Product createProduct(String name, int stock) {
            return productRepository.save(Product.builder()
                    .name(name)
                    .description("테스트 상품")
                    .price(BigDecimal.valueOf(10000))
                    .stock(stock)
                    .safetyStock(10)
                    .category(testCategory)
                    .status(ProductStatus.AVAILABLE)
                    .build());
        }

        private void createStatistics(Product product, LocalDate date, int salesCount, BigDecimal salesAmount) {
            productStatisticsRepository.save(ProductStatistics.builder()
                    .product(product)
                    .statisticsDate(date)
                    .salesCount(salesCount)
                    .salesAmount(salesAmount)
                    .viewCount(0)
                    .build());
        }
    }

    @Nested
    @DisplayName("Repository 동시성 및 트랜잭션 테스트")
    class ConcurrencyAndTransactionTest {

        @Test
        @DisplayName("성공: 낙관적 락 동작 확인 (@Version)")
        @org.springframework.transaction.annotation.Transactional
        void optimisticLock_Test() {
            // Given
            Product saved = productRepository.save(Product.builder()
                    .name("락 테스트 상품")
                    .price(BigDecimal.valueOf(10000))
                    .stock(100)
                    .safetyStock(10)
                    .category(testCategory)
                    .status(ProductStatus.AVAILABLE)
                    .build());

            // When - 낙관적 락으로 조회
            Optional<Product> locked = productRepository.findByIdWithLock(saved.getId());

            // Then
            assertThat(locked).isPresent();
            assertThat(locked.get().getVersion()).isNotNull();
            assertThat(locked.get().getId()).isEqualTo(saved.getId());
        }

        @Test
        @DisplayName("성공: JPA 영속성 컨텍스트 동작 확인")
        void jpa_PersistenceContext() {
            // Given
            Product product = productRepository.save(Product.builder()
                    .name("영속성 테스트")
                    .price(BigDecimal.valueOf(10000))
                    .stock(100)
                    .safetyStock(10)
                    .category(testCategory)
                    .status(ProductStatus.AVAILABLE)
                    .build());

            // When - 재고 감소 (도메인 로직)
            product.decreaseStock(30);
            productRepository.save(product);

            // Then - DB에서 다시 조회하여 확인
            Product found = productRepository.findById(product.getId()).orElseThrow();
            assertThat(found.getStock()).isEqualTo(70);
            assertThat(found.getStatus()).isEqualTo(ProductStatus.AVAILABLE);
        }

        @Test
        @DisplayName("성공: 재고 0 되면 상태 자동 변경")
        void stock_AutoStatusChange() {
            // Given
            Product product = productRepository.save(Product.builder()
                    .name("상태변경 테스트")
                    .price(BigDecimal.valueOf(10000))
                    .stock(50)
                    .safetyStock(10)
                    .category(testCategory)
                    .status(ProductStatus.AVAILABLE)
                    .build());

            // When - 재고 전부 소진
            product.decreaseStock(50);
            productRepository.save(product);

            // Then
            Product found = productRepository.findById(product.getId()).orElseThrow();
            assertThat(found.getStock()).isZero();
            assertThat(found.getStatus()).isEqualTo(ProductStatus.OUT_OF_STOCK);
        }

        @Test
        @DisplayName("성공: 카테고리 FK 제약조건 동작")
        @org.springframework.transaction.annotation.Transactional
        void category_ForeignKeyConstraint() {
            // Given
            Product product = productRepository.save(Product.builder()
                    .name("FK 테스트")
                    .price(BigDecimal.valueOf(10000))
                    .stock(100)
                    .safetyStock(10)
                    .category(testCategory)
                    .status(ProductStatus.AVAILABLE)
                    .build());

            // When - 조회
            Product found = productRepository.findById(product.getId()).orElseThrow();

            // Then - 카테고리 관계 확인 (Lazy Loading)
            assertThat(found.getCategory()).isNotNull();
            assertThat(found.getCategory().getId()).isEqualTo(testCategory.getId());
            assertThat(found.getCategory().getName()).isEqualTo(testCategory.getName());
        }
    }
}

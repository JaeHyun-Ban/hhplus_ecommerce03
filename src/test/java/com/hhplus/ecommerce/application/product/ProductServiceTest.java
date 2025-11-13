package com.hhplus.ecommerce.application.product;

import com.hhplus.ecommerce.common.FakeRepositorySupport;
import com.hhplus.ecommerce.domain.product.Category;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductStatistics;
import com.hhplus.ecommerce.domain.product.ProductStatus;
import com.hhplus.ecommerce.infrastructure.persistence.product.ProductRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.ProductStatisticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * ProductService 단위 테스트
 *
 * 테스트 전략:
 * - 인메모리 데이터(Map, List) 사용
 * - Given-When-Then 패턴
 */
@DisplayName("ProductService 단위 테스트")
class ProductServiceTest {

    private ProductRepository productRepository;
    private ProductStatisticsRepository productStatisticsRepository;
    private ProductService productService;

    private Category testCategory;

    /**
     * Fake ProductRepository - 인메모리 Map 사용
     */
    static class FakeProductRepository extends FakeRepositorySupport<Product, Long> implements ProductRepository {
        private final Map<Long, Product> store = new HashMap<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Override
        public Product save(Product product) {
            if (product.getId() == null) {
                Long newId = idGenerator.getAndIncrement();
                Product newProduct = Product.builder()
                        .id(newId)
                        .name(product.getName())
                        .description(product.getDescription())
                        .price(product.getPrice())
                        .stock(product.getStock())
                        .safetyStock(product.getSafetyStock())
                        .category(product.getCategory())
                        .status(product.getStatus())
                        .version(0L)
                        .build();
                store.put(newId, newProduct);
                return newProduct;
            } else {
                store.put(product.getId(), product);
                return product;
            }
        }

        @Override
        public Optional<Product> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Product> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        @Override
        public void delete(Product product) {
            store.remove(product.getId());
        }

        @Override
        public void deleteById(Long id) {
            store.remove(id);
        }

        @Override
        public boolean existsById(Long id) {
            return store.containsKey(id);
        }

        @Override
        public List<Product> findAllById(Iterable<Long> ids) {
            List<Long> idList = new ArrayList<>();
            ids.forEach(idList::add);

            return idList.stream()
                    .map(store::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        @Override
        public Page<Product> findAvailableProducts(Pageable pageable) {
            List<Product> availableProducts = store.values().stream()
                    .filter(p -> p.getStatus() == ProductStatus.AVAILABLE)
                    .sorted(Comparator.comparing(Product::getId))
                    .skip(pageable.getOffset())
                    .limit(pageable.getPageSize())
                    .collect(Collectors.toList());

            long total = store.values().stream()
                    .filter(p -> p.getStatus() == ProductStatus.AVAILABLE)
                    .count();

            return new PageImpl<>(availableProducts, pageable, total);
        }

        @Override
        public Page<Product> findByCategoryId(Long categoryId, Pageable pageable) {
            List<Product> products = store.values().stream()
                    .filter(p -> p.getCategory().getId().equals(categoryId))
                    .sorted(Comparator.comparing(Product::getId))
                    .skip(pageable.getOffset())
                    .limit(pageable.getPageSize())
                    .collect(Collectors.toList());

            long total = store.values().stream()
                    .filter(p -> p.getCategory().getId().equals(categoryId))
                    .count();

            return new PageImpl<>(products, pageable, total);
        }

        @Override
        public Optional<Product> findByIdWithLock(Long id) {
            return findById(id);
        }

        @Override
        public List<Product> findLowStockProducts() {
            return new ArrayList<>();
        }

        @Override
        public List<Product> findByStatus(ProductStatus status) {
            return new ArrayList<>();
        }

        public void clear() {
            store.clear();
            idGenerator.set(1);
        }
    }

    /**
     * Fake ProductStatisticsRepository - 인메모리 List 사용
     */
    static class FakeProductStatisticsRepository extends FakeRepositorySupport<ProductStatistics, Long> implements ProductStatisticsRepository {
        private final List<ProductStatistics> store = new ArrayList<>();

        @Override
        public ProductStatistics save(ProductStatistics statistics) {
            store.add(statistics);
            return statistics;
        }

        @Override
        public Optional<ProductStatistics> findById(Long id) {
            return store.stream()
                    .filter(stat -> stat.getId() != null && stat.getId().equals(id))
                    .findFirst();
        }

        @Override
        public List<ProductStatistics> findAll() {
            return new ArrayList<>(store);
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        @Override
        public void delete(ProductStatistics entity) {
            store.removeIf(s -> s.getId() != null && s.getId().equals(entity.getId()));
        }

        @Override
        public void deleteById(Long id) {
            store.removeIf(s -> s.getId() != null && s.getId().equals(id));
        }

        @Override
        public boolean existsById(Long id) {
            return store.stream().anyMatch(s -> s.getId() != null && s.getId().equals(id));
        }

        @Override
        public List<ProductStatistics> findAllById(Iterable<Long> ids) {
            return new ArrayList<>();
        }

        @Override
        public List<Long> findTopProductIdsByDateRange(LocalDate startDate, LocalDate endDate, int limit) {
            return store.stream()
                    .filter(stat -> !stat.getStatisticsDate().isBefore(startDate) &&
                                    !stat.getStatisticsDate().isAfter(endDate))
                    .sorted(Comparator.comparing(ProductStatistics::getSalesCount).reversed())
                    .limit(limit)
                    .map(stat -> stat.getProduct().getId())
                    .collect(Collectors.toList());
        }

        @Override
        public Optional<ProductStatistics> findByProductIdAndDate(Long productId, LocalDate date) {
            return Optional.empty();
        }

        @Override
        public List<ProductStatistics> findByProductIdAndDateRange(Long productId, LocalDate startDate, LocalDate endDate) {
            return new ArrayList<>();
        }

        public void clear() {
            store.clear();
        }
    }

    @BeforeEach
    void setUp() {
        FakeProductRepository fakeProductRepo = new FakeProductRepository();
        FakeProductStatisticsRepository fakeStatsRepo = new FakeProductStatisticsRepository();

        fakeProductRepo.clear();
        fakeStatsRepo.clear();

        productRepository = fakeProductRepo;
        productStatisticsRepository = fakeStatsRepo;
        productService = new ProductService(productRepository, productStatisticsRepository);

        // 테스트용 카테고리 생성
        testCategory = createCategory(1L, "전자제품");
    }

    @Nested
    @DisplayName("상품 목록 조회 테스트")
    class GetAvailableProductsTest {

        @Test
        @DisplayName("성공: 판매 가능한 상품 목록 조회")
        void getAvailableProducts_Success() {
            // Given
            Product product1 = createAndSaveProduct("노트북", 50, ProductStatus.AVAILABLE);
            Product product2 = createAndSaveProduct("마우스", 100, ProductStatus.AVAILABLE);
            Product product3 = createAndSaveProduct("키보드", 80, ProductStatus.AVAILABLE);

            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Product> result = productService.getAvailableProducts(pageable);

            // Then
            assertThat(result.getContent()).hasSize(3);
            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getContent().get(0).getName()).isEqualTo("노트북");
            assertThat(result.getContent().get(1).getName()).isEqualTo("마우스");
            assertThat(result.getContent().get(2).getName()).isEqualTo("키보드");
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
            createAndSaveProduct("노트북", 50, ProductStatus.AVAILABLE);
            createAndSaveProduct("품절상품", 0, ProductStatus.OUT_OF_STOCK);
            createAndSaveProduct("마우스", 100, ProductStatus.AVAILABLE);

            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Product> result = productService.getAvailableProducts(pageable);

            // Then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent()).extracting(Product::getName)
                    .containsExactlyInAnyOrder("노트북", "마우스");
        }
    }

    @Nested
    @DisplayName("카테고리별 상품 목록 조회 테스트")
    class GetProductsByCategoryTest {

        @Test
        @DisplayName("성공: 특정 카테고리의 상품 목록 조회")
        void getProductsByCategory_Success() {
            // Given
            Long categoryId = 1L;
            createAndSaveProduct("노트북", 50, ProductStatus.AVAILABLE);
            createAndSaveProduct("마우스", 100, ProductStatus.AVAILABLE);

            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Product> result = productService.getProductsByCategory(categoryId, pageable);

            // Then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getCategory().getId()).isEqualTo(categoryId);
        }
    }

    @Nested
    @DisplayName("상품 상세 조회 테스트")
    class GetProductTest {

        @Test
        @DisplayName("성공: 상품 상세 정보 조회")
        void getProduct_Success() {
            // Given
            Product saved = createAndSaveProduct("노트북", 50, ProductStatus.AVAILABLE);

            // When
            Product result = productService.getProduct(saved.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("노트북");
            assertThat(result.getStock()).isEqualTo(50);
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
            Product product1 = createAndSaveProduct("노트북", 50, ProductStatus.AVAILABLE);
            Product product2 = createAndSaveProduct("마우스", 100, ProductStatus.AVAILABLE);
            Product product3 = createAndSaveProduct("키보드", 80, ProductStatus.AVAILABLE);

            LocalDate today = LocalDate.now();

            // 통계 데이터 생성 (판매량: 키보드 > 노트북 > 마우스)
            createStatistics(product3.getId(), today, 100); // 키보드 1위
            createStatistics(product1.getId(), today, 80);  // 노트북 2위
            createStatistics(product2.getId(), today, 50);  // 마우스 3위

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
            createAndSaveProduct("노트북", 50, ProductStatus.AVAILABLE);
            createAndSaveProduct("마우스", 100, ProductStatus.AVAILABLE);

            // When
            List<Product> result = productService.getPopularProducts();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("노트북");
        }

        @Test
        @DisplayName("성공: 인기 상품이 5개 미만일 때")
        void getPopularProducts_LessThan5() {
            // Given
            Product product1 = createAndSaveProduct("노트북", 50, ProductStatus.AVAILABLE);
            Product product2 = createAndSaveProduct("마우스", 100, ProductStatus.AVAILABLE);

            LocalDate today = LocalDate.now();
            createStatistics(product1.getId(), today, 80);
            createStatistics(product2.getId(), today, 50);

            // When
            List<Product> result = productService.getPopularProducts();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(product1.getId());
            assertThat(result.get(1).getId()).isEqualTo(product2.getId());
        }
    }

    // ========================================
    // 테스트 데이터 생성 헬퍼 메서드
    // ========================================

    private Category createCategory(Long id, String name) {
        return Category.builder()
            .id(id)
            .name(name)
            .description("테스트 카테고리")
            .createdAt(LocalDateTime.now())
            .build();
    }

    private Product createAndSaveProduct(String name, int stock, ProductStatus status) {
        Product product = Product.builder()
            .name(name)
            .description(name + " 설명")
            .price(BigDecimal.valueOf(100000))
            .stock(stock)
            .safetyStock(10)
            .category(testCategory)
            .status(status)
            .version(0L)
            .build();

        return productRepository.save(product);
    }

    private void createStatistics(Long productId, LocalDate date, int salesCount) {
        Product product = productRepository.findById(productId).orElse(null);
        ProductStatistics stats = ProductStatistics.builder()
                .product(product)
                .statisticsDate(date)
                .salesCount(salesCount)
                .salesAmount(BigDecimal.valueOf(salesCount * 100000))
                .viewCount(0)
                .build();

        productStatisticsRepository.save(stats);
    }
}

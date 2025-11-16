package com.hhplus.ecommerce.application.product;

import com.hhplus.ecommerce.common.FakeRepositorySupport;
import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderItem;
import com.hhplus.ecommerce.domain.order.OrderStatus;
import com.hhplus.ecommerce.domain.product.Category;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductStatistics;
import com.hhplus.ecommerce.domain.product.ProductStatus;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRole;
import com.hhplus.ecommerce.domain.user.UserStatus;
import com.hhplus.ecommerce.infrastructure.persistence.order.OrderRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.ProductRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.ProductStatisticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

/**
 * ProductStatisticsService 단위 테스트
 *
 * 테스트 전략:
 * - 인메모리 데이터(Map, List) 사용
 * - Given-When-Then 패턴
 * - 배치 집계 로직 검증
 */
@DisplayName("ProductStatisticsService 단위 테스트")
class ProductStatisticsServiceTest {

    private OrderRepository orderRepository;
    private ProductRepository productRepository;
    private ProductStatisticsRepository productStatisticsRepository;
    private ProductStatisticsService productStatisticsService;

    private User testUser;
    private Category testCategory;
    private Product testProduct1;
    private Product testProduct2;

    /**
     * Fake OrderRepository - 인메모리 List 사용
     */
    static class FakeOrderRepository extends FakeRepositorySupport<Order, Long> implements OrderRepository {
        private final List<Order> store = new ArrayList<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Override
        public Order save(Order order) {
            if (order.getId() == null) {
                Order newOrder = Order.builder()
                        .id(idGenerator.getAndIncrement())
                        .orderNumber(order.getOrderNumber())
                        .user(order.getUser())
                        .orderItems(order.getOrderItems())
                        .totalAmount(order.getTotalAmount())
                        .discountAmount(order.getDiscountAmount())
                        .finalAmount(order.getFinalAmount())
                        .status(order.getStatus())
                        .orderedAt(order.getOrderedAt())
                        .paidAt(order.getPaidAt())
                        .idempotencyKey(order.getIdempotencyKey())
                        .build();
                store.add(newOrder);
                return newOrder;
            }
            store.removeIf(o -> o.getId().equals(order.getId()));
            store.add(order);
            return order;
        }

        @Override
        public Optional<Order> findById(Long id) {
            return store.stream()
                    .filter(order -> order.getId().equals(id))
                    .findFirst();
        }

        @Override
        public List<Order> findAll() {
            return new ArrayList<>(store);
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        @Override
        public void deleteById(Long id) {
            findById(id).ifPresent(this::delete);
        }

        @Override
        public void delete(Order entity) {
            store.removeIf(o -> o.getId().equals(entity.getId()));
        }

        @Override
        public boolean existsById(Long id) {
            return findById(id).isPresent();
        }

        @Override
        public List<Order> findAllById(Iterable<Long> ids) {
            return new ArrayList<>();
        }

        @Override
        public Optional<Order> findByIdWithDetails(Long id) {
            return findById(id);
        }

        @Override
        public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
            return Optional.empty();
        }

        @Override
        public Optional<Order> findByOrderNumber(String orderNumber) {
            return Optional.empty();
        }

        @Override
        public org.springframework.data.domain.Page<Order> findByUserOrderByOrderedAtDesc(
                User user, org.springframework.data.domain.Pageable pageable) {
            return org.springframework.data.domain.Page.empty();
        }

        @Override
        public org.springframework.data.domain.Page<Order> findByUserAndStatus(
                User user, OrderStatus status, org.springframework.data.domain.Pageable pageable) {
            return org.springframework.data.domain.Page.empty();
        }

        @Override
        public Long countOrdersBetween(LocalDateTime startOfDay, LocalDateTime endOfDay) {
            return store.stream()
                    .filter(order -> !order.getOrderedAt().isBefore(startOfDay) && order.getOrderedAt().isBefore(endOfDay))
                    .count();
        }

        @Override
        public List<Order> findByOrderedAtBetween(LocalDateTime startDate, LocalDateTime endDate) {
            return store.stream()
                    .filter(order -> !order.getOrderedAt().isBefore(startDate) && order.getOrderedAt().isBefore(endDate))
                    .toList();
        }

        public void clear() {
            store.clear();
            idGenerator.set(1);
        }
    }

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
            }
            store.put(product.getId(), product);
            return product;
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
            return new ArrayList<>();
        }

        @Override
        public org.springframework.data.domain.Page<Product> findAvailableProducts(
                org.springframework.data.domain.Pageable pageable) {
            return org.springframework.data.domain.Page.empty();
        }

        @Override
        public org.springframework.data.domain.Page<Product> findByCategoryId(
                Long categoryId, org.springframework.data.domain.Pageable pageable) {
            return org.springframework.data.domain.Page.empty();
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
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Override
        public ProductStatistics save(ProductStatistics statistics) {
            if (statistics.getId() == null) {
                ProductStatistics newStatistics = ProductStatistics.builder()
                        .id(idGenerator.getAndIncrement())
                        .product(statistics.getProduct())
                        .statisticsDate(statistics.getStatisticsDate())
                        .salesCount(statistics.getSalesCount())
                        .salesAmount(statistics.getSalesAmount())
                        .viewCount(statistics.getViewCount())
                        .build();
                store.add(newStatistics);
                return newStatistics;
            }
            store.removeIf(s -> s.getId().equals(statistics.getId()));
            store.add(statistics);
            return statistics;
        }

        @Override
        public Optional<ProductStatistics> findById(Long id) {
            return store.stream()
                    .filter(stat -> stat.getId().equals(id))
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
            store.removeIf(s -> s.getId().equals(entity.getId()));
        }

        @Override
        public void deleteById(Long id) {
            store.removeIf(s -> s.getId().equals(id));
        }

        @Override
        public boolean existsById(Long id) {
            return store.stream().anyMatch(s -> s.getId().equals(id));
        }

        @Override
        public List<ProductStatistics> findAllById(Iterable<Long> ids) {
            return new ArrayList<>();
        }

        @Override
        public List<Long> findTopProductIdsByDateRange(LocalDate startDate, LocalDate endDate, int limit) {
            return Collections.emptyList();
        }

        @Override
        public Optional<ProductStatistics> findByProductIdAndDate(Long productId, LocalDate date) {
            return store.stream()
                    .filter(stat -> stat.getProduct().getId().equals(productId) &&
                                    stat.getStatisticsDate().equals(date))
                    .findFirst();
        }

        @Override
        public List<ProductStatistics> findByProductIdAndDateRange(Long productId, LocalDate startDate, LocalDate endDate) {
            return store.stream()
                    .filter(stat -> stat.getProduct().getId().equals(productId) &&
                                    !stat.getStatisticsDate().isBefore(startDate) &&
                                    !stat.getStatisticsDate().isAfter(endDate))
                    .toList();
        }

        public void clear() {
            store.clear();
            idGenerator.set(1);
        }
    }

    @BeforeEach
    void setUp() {
        FakeOrderRepository fakeOrderRepo = new FakeOrderRepository();
        FakeProductRepository fakeProductRepo = new FakeProductRepository();
        FakeProductStatisticsRepository fakeStatsRepo = new FakeProductStatisticsRepository();

        fakeOrderRepo.clear();
        fakeProductRepo.clear();
        fakeStatsRepo.clear();

        orderRepository = fakeOrderRepo;
        productRepository = fakeProductRepo;
        productStatisticsRepository = fakeStatsRepo;
        productStatisticsService = new ProductStatisticsService(
                orderRepository,
                productRepository,
                productStatisticsRepository
        );

        // 테스트 데이터 생성
        testCategory = createCategory(1L, "전자제품");
        testUser = createUser(1L, "test@test.com");
        testProduct1 = createProduct(1L, "노트북", BigDecimal.valueOf(1000000), 10);
        testProduct2 = createProduct(2L, "마우스", BigDecimal.valueOf(30000), 20);

        productRepository.save(testProduct1);
        productRepository.save(testProduct2);
    }

    @Nested
    @DisplayName("일일 통계 집계 테스트")
    class AggregateDailyStatisticsTest {

        @Test
        @DisplayName("성공: 전일 주문 데이터 집계")
        void aggregateDailyStatistics_Success() {
            // Given
            LocalDate targetDate = LocalDate.of(2025, 11, 6);

            // 전일 주문 생성
            Order order1 = createOrder(1L, testUser, targetDate);
            order1.getOrderItems().add(createOrderItem(1L, order1, testProduct1, 2)); // 노트북 2개
            order1.getOrderItems().add(createOrderItem(2L, order1, testProduct2, 1)); // 마우스 1개

            Order order2 = createOrder(2L, testUser, targetDate);
            order2.getOrderItems().add(createOrderItem(3L, order2, testProduct1, 1)); // 노트북 1개

            orderRepository.save(order1);
            orderRepository.save(order2);

            // When
            int result = productStatisticsService.aggregateDailyStatistics(targetDate);

            // Then
            assertThat(result).isEqualTo(2); // 2개 상품 집계

            // 노트북 통계 확인 (총 3개 판매)
            ProductStatistics stat1 = productStatisticsRepository
                    .findByProductIdAndDate(testProduct1.getId(), targetDate)
                    .orElseThrow();
            assertThat(stat1.getSalesCount()).isEqualTo(3); // 2 + 1
            assertThat(stat1.getSalesAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000000)); // 1M * 3

            // 마우스 통계 확인 (총 1개 판매)
            ProductStatistics stat2 = productStatisticsRepository
                    .findByProductIdAndDate(testProduct2.getId(), targetDate)
                    .orElseThrow();
            assertThat(stat2.getSalesCount()).isEqualTo(1);
            assertThat(stat2.getSalesAmount()).isEqualByComparingTo(BigDecimal.valueOf(30000));
        }

        @Test
        @DisplayName("성공: 주문이 없으면 0 반환")
        void aggregateDailyStatistics_NoOrders() {
            // Given
            LocalDate targetDate = LocalDate.of(2025, 11, 6);

            // When
            int result = productStatisticsService.aggregateDailyStatistics(targetDate);

            // Then
            assertThat(result).isEqualTo(0);
            assertThat(productStatisticsRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("성공: 기존 통계가 있으면 업데이트")
        void aggregateDailyStatistics_UpdateExisting() {
            // Given
            LocalDate targetDate = LocalDate.of(2025, 11, 6);

            // 기존 통계 생성
            ProductStatistics existingStats = ProductStatistics.builder()
                    .product(testProduct1)
                    .statisticsDate(targetDate)
                    .salesCount(10)
                    .salesAmount(BigDecimal.valueOf(10000000))
                    .viewCount(100)
                    .build();
            productStatisticsRepository.save(existingStats);

            // 새 주문 생성
            Order order = createOrder(1L, testUser, targetDate);
            order.getOrderItems().add(createOrderItem(1L, order, testProduct1, 2));
            orderRepository.save(order);

            // When
            int result = productStatisticsService.aggregateDailyStatistics(targetDate);

            // Then
            assertThat(result).isEqualTo(1);

            ProductStatistics updated = productStatisticsRepository
                    .findByProductIdAndDate(testProduct1.getId(), targetDate)
                    .orElseThrow();
            assertThat(updated.getSalesCount()).isEqualTo(12); // 10 + 2
            assertThat(updated.getSalesAmount())
                    .isEqualByComparingTo(BigDecimal.valueOf(12000000)); // 10M + 2M
        }

        @Test
        @DisplayName("성공: 결제 완료된 주문만 집계")
        void aggregateDailyStatistics_OnlyPaidOrders() {
            // Given
            LocalDate targetDate = LocalDate.of(2025, 11, 6);

            // PAID 주문
            Order paidOrder = createOrder(1L, testUser, targetDate);
            paidOrder.getOrderItems().add(createOrderItem(1L, paidOrder, testProduct1, 2));
            orderRepository.save(paidOrder);

            // PENDING 주문 (집계 제외)
            Order pendingOrder = createOrderWithStatus(2L, testUser, targetDate, OrderStatus.PENDING);
            pendingOrder.getOrderItems().add(createOrderItem(2L, pendingOrder, testProduct1, 1));
            orderRepository.save(pendingOrder);

            // When
            int result = productStatisticsService.aggregateDailyStatistics(targetDate);

            // Then
            assertThat(result).isEqualTo(1);

            ProductStatistics stat = productStatisticsRepository
                    .findByProductIdAndDate(testProduct1.getId(), targetDate)
                    .orElseThrow();
            assertThat(stat.getSalesCount()).isEqualTo(2); // PAID 주문만 집계
        }

        @Test
        @DisplayName("성공: 여러 날짜의 주문이 있어도 대상 날짜만 집계")
        void aggregateDailyStatistics_OnlyTargetDate() {
            // Given
            LocalDate targetDate = LocalDate.of(2025, 11, 6);
            LocalDate otherDate = LocalDate.of(2025, 11, 5);

            // 대상 날짜 주문
            Order targetOrder = createOrder(1L, testUser, targetDate);
            targetOrder.getOrderItems().add(createOrderItem(1L, targetOrder, testProduct1, 2));
            orderRepository.save(targetOrder);

            // 다른 날짜 주문 (집계 제외)
            Order otherOrder = createOrder(2L, testUser, otherDate);
            otherOrder.getOrderItems().add(createOrderItem(2L, otherOrder, testProduct1, 5));
            orderRepository.save(otherOrder);

            // When
            int result = productStatisticsService.aggregateDailyStatistics(targetDate);

            // Then
            assertThat(result).isEqualTo(1);

            ProductStatistics stat = productStatisticsRepository
                    .findByProductIdAndDate(testProduct1.getId(), targetDate)
                    .orElseThrow();
            assertThat(stat.getSalesCount()).isEqualTo(2); // 대상 날짜만
        }
    }

    // ========================================
    // 테스트 데이터 생성 헬퍼 메서드
    // ========================================

    private User createUser(Long id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .password("password")
                .name("테스트사용자")
                .balance(BigDecimal.valueOf(10000000))
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private Category createCategory(Long id, String name) {
        return Category.builder()
                .id(id)
                .name(name)
                .description("테스트 카테고리")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Product createProduct(Long id, String name, BigDecimal price, int stock) {
        return Product.builder()
                .id(id)
                .name(name)
                .description(name + " 설명")
                .price(price)
                .stock(stock)
                .safetyStock(5)
                .category(testCategory)
                .status(ProductStatus.AVAILABLE)
                .version(0L)
                .build();
    }

    private Order createOrder(Long id, User user, LocalDate orderDate) {
        return Order.builder()
                .id(id)
                .orderNumber("ORD-" + orderDate.toString() + "-" + id)
                .user(user)
                .orderItems(new ArrayList<>())
                .totalAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(BigDecimal.ZERO)
                .status(OrderStatus.PAID)
                .orderedAt(orderDate.atStartOfDay())
                .paidAt(orderDate.atStartOfDay())
                .idempotencyKey("key-" + id)
                .build();
    }

    private Order createOrderWithStatus(Long id, User user, LocalDate orderDate, OrderStatus status) {
        return Order.builder()
                .id(id)
                .orderNumber("ORD-" + orderDate.toString() + "-" + id)
                .user(user)
                .orderItems(new ArrayList<>())
                .totalAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(BigDecimal.ZERO)
                .status(status)
                .orderedAt(orderDate.atStartOfDay())
                .paidAt(status == OrderStatus.PAID ? orderDate.atStartOfDay() : null)
                .idempotencyKey("key-" + id)
                .build();
    }

    private OrderItem createOrderItem(Long id, Order order, Product product, int quantity) {
        return OrderItem.builder()
                .id(id)
                .order(order)
                .product(product)
                .quantity(quantity)
                .price(product.getPrice())
                .build();
    }
}

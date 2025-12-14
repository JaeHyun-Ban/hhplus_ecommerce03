package com.hhplus.ecommerce.product.application;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.order.domain.Order;
import com.hhplus.ecommerce.order.domain.OrderItem;
import com.hhplus.ecommerce.order.domain.OrderStatus;
import com.hhplus.ecommerce.product.domain.Category;
import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.domain.ProductStatistics;
import com.hhplus.ecommerce.product.domain.ProductStatus;
import com.hhplus.ecommerce.user.domain.User;
import com.hhplus.ecommerce.user.domain.UserRole;
import com.hhplus.ecommerce.user.domain.UserStatus;
import com.hhplus.ecommerce.order.infrastructure.persistence.OrderRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.CategoryRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductStatisticsRepository;
import com.hhplus.ecommerce.user.infrastructure.persistence.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProductStatisticsService 통합 테스트
 *
 * 테스트 전략:
 * - TestContainers (MySQL, Redis) 사용
 * - Given-When-Then 패턴
 * - 배치 집계 로직 검증
 */
@Slf4j
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("ProductStatisticsService 통합 테스트")
class ProductStatisticsServiceTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    static {
        redis.start();
        System.setProperty("spring.data.redis.host", redis.getHost());
        System.setProperty("spring.data.redis.port", redis.getMappedPort(6379).toString());
    }

    @Autowired
    private ProductStatisticsService productStatisticsService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private com.hhplus.ecommerce.order.infrastructure.persistence.PaymentRepository paymentRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductStatisticsRepository productStatisticsRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private Category testCategory;
    private Product testProduct1;
    private Product testProduct2;

    @BeforeEach
    void setUp() {
        // 데이터 정리
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        productStatisticsRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트 사용자 생성
        testUser = User.builder()
                .email("test@test.com")
                .password("password123")
                .name("테스트사용자")
                .balance(BigDecimal.valueOf(10000000))
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        testUser = userRepository.save(testUser);

        // 테스트 카테고리 생성
        testCategory = Category.builder()
                .name("전자제품")
                .description("테스트 카테고리")
                .build();
        testCategory = categoryRepository.save(testCategory);

        // 테스트 상품 생성
        testProduct1 = Product.builder()
                .name("노트북")
                .description("노트북 설명")
                .price(BigDecimal.valueOf(1000000))
                .stock(100)
                .safetyStock(10)
                .category(testCategory)
                .status(ProductStatus.AVAILABLE)
                .build();
        testProduct1 = productRepository.save(testProduct1);

        testProduct2 = Product.builder()
                .name("마우스")
                .description("마우스 설명")
                .price(BigDecimal.valueOf(30000))
                .stock(200)
                .safetyStock(20)
                .category(testCategory)
                .status(ProductStatus.AVAILABLE)
                .build();
        testProduct2 = productRepository.save(testProduct2);

        log.info("테스트 데이터 준비 완료");
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
            Order order1 = createOrder(testUser, targetDate);
            order1.getOrderItems().add(createOrderItem(order1, testProduct1, 2)); // 노트북 2개
            order1.getOrderItems().add(createOrderItem(order1, testProduct2, 1)); // 마우스 1개
            order1 = orderRepository.save(order1);

            Order order2 = createOrder(testUser, targetDate);
            order2.getOrderItems().add(createOrderItem(order2, testProduct1, 1)); // 노트북 1개
            order2 = orderRepository.save(order2);

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

            log.info("✅ 전일 주문 데이터 집계 테스트 성공");
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

            log.info("✅ 주문 없을 때 0 반환 테스트 성공");
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
            Order order = createOrder(testUser, targetDate);
            order.getOrderItems().add(createOrderItem(order, testProduct1, 2));
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

            log.info("✅ 기존 통계 업데이트 테스트 성공");
        }

        @Test
        @DisplayName("성공: 결제 완료된 주문만 집계")
        void aggregateDailyStatistics_OnlyPaidOrders() {
            // Given
            LocalDate targetDate = LocalDate.of(2025, 11, 6);

            // PAID 주문
            Order paidOrder = createOrder(testUser, targetDate);
            paidOrder.getOrderItems().add(createOrderItem(paidOrder, testProduct1, 2));
            orderRepository.save(paidOrder);

            // PENDING 주문 (집계 제외)
            Order pendingOrder = createOrderWithStatus(testUser, targetDate, OrderStatus.PENDING);
            pendingOrder.getOrderItems().add(createOrderItem(pendingOrder, testProduct1, 1));
            orderRepository.save(pendingOrder);

            // When
            int result = productStatisticsService.aggregateDailyStatistics(targetDate);

            // Then
            assertThat(result).isEqualTo(1);

            ProductStatistics stat = productStatisticsRepository
                    .findByProductIdAndDate(testProduct1.getId(), targetDate)
                    .orElseThrow();
            assertThat(stat.getSalesCount()).isEqualTo(2); // PAID 주문만 집계

            log.info("✅ 결제 완료된 주문만 집계 테스트 성공");
        }

        @Test
        @DisplayName("성공: 여러 날짜의 주문이 있어도 대상 날짜만 집계")
        void aggregateDailyStatistics_OnlyTargetDate() {
            // Given
            LocalDate targetDate = LocalDate.of(2025, 11, 6);
            LocalDate otherDate = LocalDate.of(2025, 11, 5);

            // 대상 날짜 주문
            Order targetOrder = createOrder(testUser, targetDate);
            targetOrder.getOrderItems().add(createOrderItem(targetOrder, testProduct1, 2));
            orderRepository.save(targetOrder);

            // 다른 날짜 주문 (집계 제외)
            Order otherOrder = createOrder(testUser, otherDate);
            otherOrder.getOrderItems().add(createOrderItem(otherOrder, testProduct1, 5));
            orderRepository.save(otherOrder);

            // When
            int result = productStatisticsService.aggregateDailyStatistics(targetDate);

            // Then
            assertThat(result).isEqualTo(1);

            ProductStatistics stat = productStatisticsRepository
                    .findByProductIdAndDate(testProduct1.getId(), targetDate)
                    .orElseThrow();
            assertThat(stat.getSalesCount()).isEqualTo(2); // 대상 날짜만

            log.info("✅ 대상 날짜만 집계 테스트 성공");
        }
    }

    // ========================================
    // 테스트 데이터 생성 헬퍼 메서드
    // ========================================

    private Order createOrder(User user, LocalDate orderDate) {
        return Order.builder()
                .orderNumber("ORD-" + orderDate + "-" + System.nanoTime())
                .user(user)
                .orderItems(new ArrayList<>())
                .totalAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(BigDecimal.ZERO)
                .status(OrderStatus.PAID)
                .orderedAt(orderDate.atStartOfDay())
                .paidAt(orderDate.atStartOfDay())
                .idempotencyKey("key-" + System.nanoTime())
                .build();
    }

    private Order createOrderWithStatus(User user, LocalDate orderDate, OrderStatus status) {
        return Order.builder()
                .orderNumber("ORD-" + orderDate + "-" + System.nanoTime())
                .user(user)
                .orderItems(new ArrayList<>())
                .totalAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(BigDecimal.ZERO)
                .status(status)
                .orderedAt(orderDate.atStartOfDay())
                .paidAt(status == OrderStatus.PAID ? orderDate.atStartOfDay() : null)
                .idempotencyKey("key-" + System.nanoTime())
                .build();
    }

    private OrderItem createOrderItem(Order order, Product product, int quantity) {
        return OrderItem.of(order, product, quantity);
    }
}

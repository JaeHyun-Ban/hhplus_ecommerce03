package com.hhplus.ecommerce.order.application;

import com.hhplus.ecommerce.cart.domain.Cart;
import com.hhplus.ecommerce.cart.domain.CartItem;
import com.hhplus.ecommerce.cart.infrastructure.persistence.CartRepository;
import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.coupon.domain.Coupon;
import com.hhplus.ecommerce.coupon.domain.CouponStatus;
import com.hhplus.ecommerce.coupon.domain.CouponType;
import com.hhplus.ecommerce.coupon.domain.UserCoupon;
import com.hhplus.ecommerce.coupon.domain.UserCouponStatus;
import com.hhplus.ecommerce.coupon.infrastructure.persistence.CouponRepository;
import com.hhplus.ecommerce.coupon.infrastructure.persistence.UserCouponRepository;
import com.hhplus.ecommerce.order.domain.Order;
import com.hhplus.ecommerce.order.domain.OrderStatus;
import com.hhplus.ecommerce.order.domain.Payment;
import com.hhplus.ecommerce.order.domain.PaymentStatus;
import com.hhplus.ecommerce.order.infrastructure.persistence.OrderRepository;
import com.hhplus.ecommerce.product.domain.Category;
import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.domain.ProductStatus;
import com.hhplus.ecommerce.product.domain.StockHistory;
import com.hhplus.ecommerce.product.infrastructure.persistence.CategoryRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.StockHistoryRepository;
import com.hhplus.ecommerce.user.domain.BalanceHistory;
import com.hhplus.ecommerce.user.domain.User;
import com.hhplus.ecommerce.user.domain.UserRole;
import com.hhplus.ecommerce.user.domain.UserStatus;
import com.hhplus.ecommerce.user.infrastructure.persistence.BalanceHistoryRepository;
import com.hhplus.ecommerce.user.infrastructure.persistence.UserRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

/**
 * 이벤트 리스너 통합 테스트 (TestContainers 사용)
 *
 * 테스트 범위:
 * - StockDeductionEventListener: 재고 차감 이벤트 처리
 * - BalanceDeductionEventListener: 잔액 차감 이벤트 처리
 * - CouponUsageEventListener: 쿠폰 사용 이벤트 처리
 * - PopularProductEventListener: 인기상품 집계 이벤트 처리
 *
 * 테스트 전략:
 * - 비동기 이벤트 리스너의 실행 완료를 Awaitility로 대기
 * - 실제 MySQL 컨테이너로 트랜잭션 분리 검증
 * - 보상 트랜잭션 동작 확인
 * - 이벤트 소싱 확인
 */
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("이벤트 리스너 통합 테스트")
class OrderEventListenerIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    static {
        redis.start();
        System.setProperty("spring.data.redis.host", redis.getHost());
        System.setProperty("spring.data.redis.port", redis.getMappedPort(6379).toString());
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private BalanceHistoryRepository balanceHistoryRepository;

    @Autowired
    private StockHistoryRepository stockHistoryRepository;

    private User testUser;
    private Product testProduct;
    private Category testCategory;
    private Cart testCart;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 정리 (외래 키 제약 조건 고려)
        balanceHistoryRepository.deleteAll();
        stockHistoryRepository.deleteAll();
        orderRepository.deleteAll();  // orders를 먼저 삭제 (order_coupons cascade)
        cartRepository.deleteAll();
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        // 기본 테스트 데이터 생성
        testCategory = Category.builder()
            .name("전자제품")
            .description("전자제품 카테고리")
            .build();
        testCategory = categoryRepository.save(testCategory);

        testProduct = Product.builder()
            .name("테스트 상품")
            .price(BigDecimal.valueOf(10000))
            .stock(100)
            .safetyStock(10)
            .status(ProductStatus.AVAILABLE)
            .category(testCategory)
            .description("테스트용 상품")
            .build();
        testProduct = productRepository.save(testProduct);

        testUser = User.builder()
            .email("test@example.com")
            .password("password123")
            .name("테스트 사용자")
            .balance(BigDecimal.valueOf(1000000))
            .status(UserStatus.ACTIVE)
            .role(UserRole.USER)
            .build();
        testUser = userRepository.save(testUser);

        testCart = Cart.builder()
            .user(testUser)
            .build();
        testCart = cartRepository.save(testCart);
    }

    @Nested
    @DisplayName("정상 주문 생성 시나리오")
    class SuccessfulOrderCreationScenario {

        @Test
        @DisplayName("재고 차감 이벤트가 성공적으로 처리되어야 한다")
        void stockDeductionEventShouldBeProcessedSuccessfully() {
            // Given
            testCart.addItem(testProduct, 2);
            cartRepository.save(testCart);

            int initialStock = testProduct.getStock();
            String idempotencyKey = UUID.randomUUID().toString();

            // When
            Order order = orderService.createOrder(testUser.getId(), null, idempotencyKey);

            // Then - 비동기 이벤트 처리 대기
            await().atMost(java.time.Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    // 재고가 차감되었는지 확인
                    Product updatedProduct = productRepository.findById(testProduct.getId())
                        .orElseThrow();
                    assertThat(updatedProduct.getStock()).isEqualTo(initialStock - 2);

                    // 재고 이력이 기록되었는지 확인
                    List<StockHistory> stockHistories = stockHistoryRepository.findAll();
                    assertThat(stockHistories).isNotEmpty();
                    assertThat(stockHistories.get(0).getQuantity()).isEqualTo(2);
                    assertThat(stockHistories.get(0).getStockBefore()).isEqualTo(initialStock);
                    assertThat(stockHistories.get(0).getStockAfter()).isEqualTo(initialStock - 2);
                });
        }

        @Test
        @DisplayName("잔액 차감 이벤트가 성공적으로 처리되어야 한다")
        void balanceDeductionEventShouldBeProcessedSuccessfully() {
            // Given
            testCart.addItem(testProduct, 1);
            cartRepository.save(testCart);

            BigDecimal initialBalance = testUser.getBalance();
            BigDecimal orderAmount = testProduct.getPrice();
            String idempotencyKey = UUID.randomUUID().toString();

            // When
            Order order = orderService.createOrder(testUser.getId(), null, idempotencyKey);

            // Then - 비동기 이벤트 처리 대기
            await().atMost(java.time.Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    // 잔액이 차감되었는지 확인
                    User updatedUser = userRepository.findById(testUser.getId())
                        .orElseThrow();
                    assertThat(updatedUser.getBalance())
                        .isEqualByComparingTo(initialBalance.subtract(orderAmount));

                    // 잔액 이력이 기록되었는지 확인
                    List<BalanceHistory> balanceHistories = balanceHistoryRepository.findAll();
                    assertThat(balanceHistories).isNotEmpty();
                    assertThat(balanceHistories.get(0).getAmount())
                        .isEqualByComparingTo(orderAmount);
                    assertThat(balanceHistories.get(0).getBalanceBefore())
                        .isEqualByComparingTo(initialBalance);
                });
        }

        @Test
        @DisplayName("주문과 결제 상태가 PAID와 COMPLETED로 변경되어야 한다")
        void orderAndPaymentStatusShouldBeUpdatedToPaidAndCompleted() {
            // Given
            testCart.addItem(testProduct, 1);
            cartRepository.save(testCart);

            String idempotencyKey = UUID.randomUUID().toString();

            // When
            Order order = orderService.createOrder(testUser.getId(), null, idempotencyKey);

            // Then - 비동기 이벤트 처리 대기
            await().atMost(java.time.Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Order updatedOrder = orderRepository.findById(order.getId())
                        .orElseThrow();

                    // 주문 상태가 PAID로 변경되었는지 확인
                    assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
                    assertThat(updatedOrder.getPaidAt()).isNotNull();

                    // Payment 상태가 COMPLETED로 변경되었는지 확인
                    Payment payment = updatedOrder.getPayment();
                    assertThat(payment).isNotNull();
                    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
                    assertThat(payment.getCompletedAt()).isNotNull();
                });
        }

        @Test
        @DisplayName("쿠폰 사용 이벤트가 성공적으로 처리되어야 한다")
        void couponUsageEventShouldBeProcessedSuccessfully() {
            // Given
            testCart.addItem(testProduct, 1);
            cartRepository.save(testCart);

            // 쿠폰 생성
            Coupon coupon = Coupon.builder()
                .name("테스트 쿠폰")
                .code("TEST1000")
                .type(CouponType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(1000))
                .minimumOrderAmount(BigDecimal.valueOf(5000))
                .totalQuantity(10)
                .issuedQuantity(0)
                .status(CouponStatus.ACTIVE)
                .issueStartAt(LocalDateTime.now().minusDays(1))
                .issueEndAt(LocalDateTime.now().plusDays(7))
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(7))
                .maxIssuePerUser(1)
                .build();
            coupon = couponRepository.save(coupon);

            UserCoupon savedUserCoupon = userCouponRepository.save(UserCoupon.builder()
                .user(testUser)
                .coupon(coupon)
                .status(UserCouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .build());

            String idempotencyKey = UUID.randomUUID().toString();

            // When
            Order order = orderService.createOrder(testUser.getId(), savedUserCoupon.getId(), idempotencyKey);

            // Then - 비동기 이벤트 처리 대기
            await().atMost(java.time.Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    // 쿠폰이 사용됨으로 표시되었는지 확인
                    UserCoupon updatedUserCoupon = userCouponRepository.findById(savedUserCoupon.getId())
                        .orElseThrow();
                    assertThat(updatedUserCoupon.getStatus()).isEqualTo(UserCouponStatus.USED);
                    assertThat(updatedUserCoupon.getUsedAt()).isNotNull();

                    // 주문에 할인이 적용되었는지 확인
                    Order updatedOrder = orderRepository.findById(order.getId())
                        .orElseThrow();
                    assertThat(updatedOrder.getDiscountAmount())
                        .isEqualByComparingTo(BigDecimal.valueOf(1000));
                });
        }
    }

    @Nested
    @DisplayName("재고 부족 시나리오 (보상 트랜잭션)")
    class StockShortageScenario {

        @Test
        @DisplayName("재고 부족 시 주문이 취소되어야 한다")
        void orderShouldBeCancelledWhenStockIsInsufficient() {
            // Given
            testProduct.decreaseStock(99); // 재고를 1개만 남김
            productRepository.save(testProduct);

            testCart.addItem(testProduct, 10); // 10개 주문 시도
            cartRepository.save(testCart);

            String idempotencyKey = UUID.randomUUID().toString();

            // When & Then
            assertThatThrownBy(() ->
                orderService.createOrder(testUser.getId(), null, idempotencyKey)
            ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재고가 부족합니다");

            // 주문이 생성되지 않았는지 확인
            await().atMost(java.time.Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    List<Order> orders = orderRepository.findAll();
                    if (!orders.isEmpty()) {
                        // 주문이 생성되었다면 CANCELLED 상태여야 함
                        assertThat(orders.get(0).getStatus()).isEqualTo(OrderStatus.CANCELLED);
                    }
                });
        }
    }

    @Nested
    @DisplayName("잔액 부족 시나리오 (보상 트랜잭션)")
    class BalanceShortageScenario {

        @Test
        @DisplayName("잔액 부족 시 재고가 복구되고 주문이 취소되어야 한다")
        void stockShouldBeRestoredAndOrderCancelledWhenBalanceIsInsufficient() {
            // Given
            testUser.useBalance(testUser.getBalance().subtract(BigDecimal.valueOf(5000))); // 잔액을 5000원만 남김
            userRepository.save(testUser);

            testCart.addItem(testProduct, 1); // 10000원 상품
            cartRepository.save(testCart);

            int initialStock = testProduct.getStock();
            String idempotencyKey = UUID.randomUUID().toString();

            // When
            Order order = orderService.createOrder(testUser.getId(), null, idempotencyKey);

            // Then - 비동기 이벤트 처리 대기
            await().atMost(java.time.Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Order updatedOrder = orderRepository.findById(order.getId())
                        .orElseThrow();

                    // 주문이 취소되었는지 확인
                    assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
                    assertThat(updatedOrder.getCancellationReason())
                        .contains("잔액");

                    // 재고가 복구되었는지 확인
                    Product updatedProduct = productRepository.findById(testProduct.getId())
                        .orElseThrow();
                    assertThat(updatedProduct.getStock()).isEqualTo(initialStock);

                    // 재고 이력에 복구 기록이 있는지 확인
                    List<StockHistory> stockHistories = stockHistoryRepository.findAll();
                    assertThat(stockHistories).hasSizeGreaterThanOrEqualTo(2); // 차감 + 복구
                });
        }
    }

    @Nested
    @DisplayName("전체 이벤트 플로우 통합 테스트")
    class CompleteEventFlowIntegrationTest {

        @Test
        @DisplayName("모든 이벤트 리스너가 순차적으로 정상 실행되어야 한다")
        void allEventListenersShouldExecuteSequentially() {
            // Given
            testCart.addItem(testProduct, 3);
            cartRepository.save(testCart);

            BigDecimal initialBalance = testUser.getBalance();
            int initialStock = testProduct.getStock();
            BigDecimal orderAmount = testProduct.getPrice().multiply(BigDecimal.valueOf(3));
            String idempotencyKey = UUID.randomUUID().toString();

            // When
            Order order = orderService.createOrder(testUser.getId(), null, idempotencyKey);

            // Then - 모든 이벤트 처리 대기 및 검증
            await().atMost(java.time.Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    Order updatedOrder = orderRepository.findByIdWithDetails(order.getId())
                        .orElseThrow();
                    User updatedUser = userRepository.findById(testUser.getId())
                        .orElseThrow();
                    Product updatedProduct = productRepository.findById(testProduct.getId())
                        .orElseThrow();

                    // 1. StockDeductionEventListener 검증
                    assertThat(updatedProduct.getStock()).isEqualTo(initialStock - 3);

                    // 2. BalanceDeductionEventListener 검증
                    assertThat(updatedUser.getBalance())
                        .isEqualByComparingTo(initialBalance.subtract(orderAmount));

                    // 3. Order 및 Payment 상태 검증
                    assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
                    assertThat(updatedOrder.getPayment().getStatus())
                        .isEqualTo(PaymentStatus.COMPLETED);

                    // 4. 이력 기록 검증
                    List<StockHistory> stockHistories = stockHistoryRepository.findAll();
                    List<BalanceHistory> balanceHistories = balanceHistoryRepository.findAll();

                    assertThat(stockHistories).hasSize(1);
                    assertThat(balanceHistories).hasSize(1);

                    assertThat(stockHistories.get(0).getReason())
                        .contains(updatedOrder.getOrderNumber());
                    assertThat(balanceHistories.get(0).getDescription())
                        .contains(updatedOrder.getOrderNumber());
                });
        }
    }
}

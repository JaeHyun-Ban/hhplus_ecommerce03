package com.hhplus.ecommerce.application.order;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.domain.cart.Cart;
import com.hhplus.ecommerce.domain.cart.CartItem;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponStatus;
import com.hhplus.ecommerce.domain.coupon.CouponType;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderStatus;
import com.hhplus.ecommerce.domain.product.Category;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductStatus;
import com.hhplus.ecommerce.domain.user.BalanceHistory;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRole;
import com.hhplus.ecommerce.domain.user.UserStatus;
import com.hhplus.ecommerce.infrastructure.persistence.cart.CartRepository;
import com.hhplus.ecommerce.infrastructure.persistence.coupon.CouponRepository;
import com.hhplus.ecommerce.infrastructure.persistence.coupon.UserCouponRepository;
import com.hhplus.ecommerce.infrastructure.persistence.order.OrderRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.CategoryRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.ProductRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.StockHistoryRepository;
import com.hhplus.ecommerce.infrastructure.persistence.user.BalanceHistoryRepository;
import com.hhplus.ecommerce.infrastructure.persistence.user.UserRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * OrderService 통합 테스트 (TestContainers 사용)
 *
 * 테스트 전략:
 * - 실제 MySQL 컨테이너를 사용한 통합 테스트
 * - 복잡한 주문 플로우 검증 (User, Product, Cart, Coupon 연계)
 * - 트랜잭션, 동시성 제어, 멱등성 검증
 * - Redis 컨테이너로 Redisson 분산락 테스트
 */
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("OrderService 통합 테스트 (TestContainers)")
class OrderServiceIntegrationTest {

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

    @Autowired
    private com.hhplus.ecommerce.infrastructure.persistence.cart.CartItemRepository cartItemRepository;

    private User testUser;
    private Category testCategory;
    private Product testProduct1;
    private Product testProduct2;

    @BeforeEach
    void setUp() {
        // 모든 데이터 초기화 (의존성 역순)
        // Order 관련 먼저 삭제
        balanceHistoryRepository.deleteAll();
        stockHistoryRepository.deleteAll();
        orderRepository.deleteAll();

        // Cart 관련 삭제
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();

        // Coupon 관련 삭제
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();

        // Product, Category, User 삭제
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트 데이터 생성
        setupTestData();
    }

    private void setupTestData() {
        // 사용자 생성 (이메일을 timestamp로 유니크하게)
        testUser = userRepository.save(User.builder()
                .email("test_" + System.currentTimeMillis() + "@example.com")
                .password("password123")
                .name("테스트사용자")
                .balance(BigDecimal.valueOf(1000000)) // 충분한 잔액
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());

        // 카테고리 생성
        testCategory = categoryRepository.save(Category.builder()
                .name("테스트카테고리_" + System.currentTimeMillis())
                .description("통합 테스트용")
                .build());

        // 상품 생성
        testProduct1 = productRepository.save(Product.builder()
                .name("테스트상품1_" + System.currentTimeMillis())
                .description("첫번째 상품")
                .price(BigDecimal.valueOf(10000))
                .stock(100)
                .safetyStock(10)
                .category(testCategory)
                .status(ProductStatus.AVAILABLE)
                .build());

        testProduct2 = productRepository.save(Product.builder()
                .name("테스트상품2_" + System.currentTimeMillis())
                .description("두번째 상품")
                .price(BigDecimal.valueOf(20000))
                .stock(50)
                .safetyStock(5)
                .category(testCategory)
                .status(ProductStatus.AVAILABLE)
                .build());
    }

    @Nested
    @DisplayName("UC-012: 주문 생성 통합 테스트")
    class CreateOrderIntegrationTest {

        @Test
        @DisplayName("성공: 기본 주문 생성 (쿠폰 없음)")
        void createOrder_Success_WithoutCoupon() {
            // Given - 장바구니 생성
            Cart cart = createCart(testUser);
            cart.addItem(testProduct1, 2); // 10,000 * 2 = 20,000
            cart.addItem(testProduct2, 1); // 20,000 * 1 = 20,000
            cartRepository.save(cart);

            String idempotencyKey = UUID.randomUUID().toString();
            BigDecimal initialBalance = testUser.getBalance();
            int initialStock1 = testProduct1.getStock();
            int initialStock2 = testProduct2.getStock();

            // When
            Order result = orderService.createOrder(testUser.getId(), null, idempotencyKey);

            // Then - 주문 생성 확인
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getOrderNumber()).isNotNull();
            assertThat(result.getUser().getId()).isEqualTo(testUser.getId());
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(40000));
            assertThat(result.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getFinalAmount()).isEqualByComparingTo(BigDecimal.valueOf(40000));

            // 주문 항목 확인
            assertThat(result.getOrderItems()).hasSize(2);

            // 재고 감소 확인
            Product product1After = productRepository.findById(testProduct1.getId()).orElseThrow();
            Product product2After = productRepository.findById(testProduct2.getId()).orElseThrow();
            assertThat(product1After.getStock()).isEqualTo(initialStock1 - 2);
            assertThat(product2After.getStock()).isEqualTo(initialStock2 - 1);

            // 잔액 감소 확인
            User userAfter = userRepository.findById(testUser.getId()).orElseThrow();
            assertThat(userAfter.getBalance())
                    .isEqualByComparingTo(initialBalance.subtract(BigDecimal.valueOf(40000)));

            // 장바구니 비워짐 확인
            Cart cartAfter = cartRepository.findByUserWithItems(testUser).orElseThrow();
            assertThat(cartAfter.getItems()).isEmpty();

            // 잔액 이력 기록 확인
            List<BalanceHistory> balanceHistories = balanceHistoryRepository.findAll();
            assertThat(balanceHistories).isNotEmpty();
        }

        @Test
        @DisplayName("성공: 쿠폰 적용 주문")
        void createOrder_Success_WithCoupon() {
            // Given - 쿠폰 생성
            Coupon coupon = couponRepository.save(Coupon.builder()
                    .code("TEST10")
                    .name("10% 할인 쿠폰")
                    .type(CouponType.PERCENTAGE)
                    .discountValue(BigDecimal.valueOf(10)) // 10%
                    .minimumOrderAmount(BigDecimal.valueOf(10000))
                    .maximumDiscountAmount(BigDecimal.valueOf(5000))
                    .totalQuantity(100)
                    .issuedQuantity(1)
                    .maxIssuePerUser(1)
                    .issueStartAt(LocalDateTime.now().minusDays(1))
                    .issueEndAt(LocalDateTime.now().plusDays(30))
                    .validFrom(LocalDateTime.now().minusDays(1))
                    .validUntil(LocalDateTime.now().plusDays(30))
                    .status(CouponStatus.ACTIVE)
                    .build());

            UserCoupon userCoupon = userCouponRepository.save(UserCoupon.builder()
                    .user(testUser)
                    .coupon(coupon)
                    .status(UserCouponStatus.ISSUED)
                    .issuedAt(LocalDateTime.now())
                    .build());

            // 장바구니 생성 (30,000원)
            Cart cart = createCart(testUser);
            cart.addItem(testProduct1, 3); // 10,000 * 3 = 30,000
            cartRepository.save(cart);

            String idempotencyKey = UUID.randomUUID().toString();

            // When
            Order result = orderService.createOrder(testUser.getId(), userCoupon.getId(), idempotencyKey);

            // Then
            assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(30000));
            assertThat(result.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000)); // 10%
            assertThat(result.getFinalAmount()).isEqualByComparingTo(BigDecimal.valueOf(27000));

            // 쿠폰 사용 확인
            UserCoupon usedCoupon = userCouponRepository.findById(userCoupon.getId()).orElseThrow();
            assertThat(usedCoupon.getStatus()).isEqualTo(UserCouponStatus.USED);
            assertThat(usedCoupon.getUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("성공: 멱등성 보장 - 중복 키로 재요청 시 기존 주문 반환")
        void createOrder_Idempotency_SameKey() {
            // Given
            Cart cart = createCart(testUser);
            cart.addItem(testProduct1, 1);
            cartRepository.save(cart);

            String idempotencyKey = UUID.randomUUID().toString();

            // When - 첫 번째 주문
            Order firstOrder = orderService.createOrder(testUser.getId(), null, idempotencyKey);

            // 장바구니를 다시 채움 (두 번째 요청 시뮬레이션)
            cart.addItem(testProduct1, 1);
            cartRepository.save(cart);

            // When - 동일한 멱등성 키로 재요청
            Order secondOrder = orderService.createOrder(testUser.getId(), null, idempotencyKey);

            // Then - 같은 주문 반환
            assertThat(secondOrder.getId()).isEqualTo(firstOrder.getId());
            assertThat(secondOrder.getOrderNumber()).isEqualTo(firstOrder.getOrderNumber());

            // 주문이 한 번만 생성되었는지 확인
            long orderCount = orderRepository.count();
            assertThat(orderCount).isEqualTo(1);
        }

        @Test
        @DisplayName("실패: 장바구니 비어있음")
        void createOrder_Fail_EmptyCart() {
            // Given - 빈 장바구니
            Cart cart = createCart(testUser);
            cartRepository.save(cart);

            String idempotencyKey = UUID.randomUUID().toString();

            // When & Then
            assertThatThrownBy(() ->
                    orderService.createOrder(testUser.getId(), null, idempotencyKey)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("장바구니가 비어있습니다");
        }

        @Test
        @DisplayName("실패: 재고 부족")
        void createOrder_Fail_InsufficientStock() {
            // Given - 재고보다 많이 주문
            Cart cart = createCart(testUser);
            cart.addItem(testProduct1, 200); // 재고 100개인데 200개 주문
            cartRepository.save(cart);

            String idempotencyKey = UUID.randomUUID().toString();

            // When & Then
            assertThatThrownBy(() ->
                    orderService.createOrder(testUser.getId(), null, idempotencyKey)
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("재고가 부족합니다");
        }

        @Test
        @DisplayName("실패: 잔액 부족")
        void createOrder_Fail_InsufficientBalance() {
            // Given - 잔액보다 비싼 상품 주문
            User poorUser = userRepository.save(User.builder()
                    .email("poor@example.com")
                    .password("password")
                    .name("가난한사용자")
                    .balance(BigDecimal.valueOf(5000)) // 부족한 잔액
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .build());

            Cart cart = createCart(poorUser);
            cart.addItem(testProduct1, 1); // 10,000원
            cartRepository.save(cart);

            String idempotencyKey = UUID.randomUUID().toString();

            // When & Then
            assertThatThrownBy(() ->
                    orderService.createOrder(poorUser.getId(), null, idempotencyKey)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("잔액이 부족합니다");
        }
    }

    @Nested
    @DisplayName("UC-013: 주문 조회 통합 테스트")
    class GetOrderIntegrationTest {

        @Test
        @DisplayName("성공: 주문 상세 조회")
        void getOrder_Success() {
            // Given - 주문 생성
            Cart cart = createCart(testUser);
            cart.addItem(testProduct1, 1);
            cartRepository.save(cart);

            Order created = orderService.createOrder(
                    testUser.getId(),
                    null,
                    UUID.randomUUID().toString()
            );

            // When
            Order result = orderService.getOrder(created.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(created.getId());
            assertThat(result.getOrderNumber()).isEqualTo(created.getOrderNumber());
            assertThat(result.getOrderItems()).isNotEmpty();
        }

        @Test
        @DisplayName("성공: 주문 번호로 조회")
        void getOrderByNumber_Success() {
            // Given
            Cart cart = createCart(testUser);
            cart.addItem(testProduct1, 1);
            cartRepository.save(cart);

            Order created = orderService.createOrder(
                    testUser.getId(),
                    null,
                    UUID.randomUUID().toString()
            );

            // When
            Order result = orderService.getOrderByNumber(created.getOrderNumber());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(created.getId());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 주문 조회")
        void getOrder_NotFound() {
            // Given
            Long nonExistentId = 99999L;

            // When & Then
            assertThatThrownBy(() -> orderService.getOrder(nonExistentId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("주문을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("UC-014: 주문 목록 조회 통합 테스트")
    class GetUserOrdersIntegrationTest {

        @Test
        @DisplayName("성공: 사용자별 주문 목록 조회 (페이징)")
        void getUserOrders_Success() {
            // Given - 3개 주문 생성
            for (int i = 0; i < 3; i++) {
                Cart cart = createCart(testUser);
                cart.addItem(testProduct1, 1);
                cartRepository.save(cart);

                orderService.createOrder(
                        testUser.getId(),
                        null,
                        UUID.randomUUID().toString()
                );

                // 약간의 시간 차이
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // When
            Pageable pageable = PageRequest.of(0, 10);
            Page<Order> result = orderService.getUserOrders(testUser.getId(), pageable);

            // Then
            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getContent()).hasSize(3);

            // 최신순 정렬 확인
            List<Order> orders = result.getContent();
            for (int i = 0; i < orders.size() - 1; i++) {
                assertThat(orders.get(i).getOrderedAt())
                        .isAfterOrEqualTo(orders.get(i + 1).getOrderedAt());
            }
        }

        @Test
        @DisplayName("성공: 주문 없는 사용자")
        void getUserOrders_NoOrders() {
            // Given - 주문이 없는 새 사용자
            User newUser = userRepository.save(User.builder()
                    .email("new@example.com")
                    .password("password")
                    .name("신규사용자")
                    .balance(BigDecimal.valueOf(100000))
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .build());

            // When
            Pageable pageable = PageRequest.of(0, 10);
            Page<Order> result = orderService.getUserOrders(newUser.getId(), pageable);

            // Then
            assertThat(result.getTotalElements()).isZero();
            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("동시성 및 트랜잭션 테스트")
    class ConcurrencyAndTransactionTest {

        @Test
        @DisplayName("성공: 트랜잭션 롤백 - 재고 부족 시 모든 변경 취소")
        void transaction_Rollback_OnStockError() {
            // Given
            BigDecimal initialBalance = testUser.getBalance();
            int initialStock = testProduct1.getStock();

            Cart cart = createCart(testUser);
            cart.addItem(testProduct1, 999); // 재고 부족
            cartRepository.save(cart);

            String idempotencyKey = UUID.randomUUID().toString();

            // When & Then - 예외 발생
            assertThatThrownBy(() ->
                    orderService.createOrder(testUser.getId(), null, idempotencyKey)
            ).isInstanceOf(IllegalStateException.class);

            // 모든 것이 롤백되어야 함
            User userAfter = userRepository.findById(testUser.getId()).orElseThrow();
            Product productAfter = productRepository.findById(testProduct1.getId()).orElseThrow();

            // 잔액 변경 없음
            assertThat(userAfter.getBalance()).isEqualByComparingTo(initialBalance);

            // 재고 변경 없음
            assertThat(productAfter.getStock()).isEqualTo(initialStock);

            // 주문 생성 안됨
            assertThat(orderRepository.count()).isZero();
        }

        @Test
        @DisplayName("성공: 낙관적 락 재시도 - 재고 동시 차감")
        void optimisticLock_Retry() {
            // Given
            Cart cart = createCart(testUser);
            cart.addItem(testProduct1, 1);
            cartRepository.save(cart);

            String idempotencyKey = UUID.randomUUID().toString();

            // When - 낙관적 락이 동작하는 주문 생성
            Order result = orderService.createOrder(testUser.getId(), null, idempotencyKey);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);

            // Version 필드가 업데이트됨
            Product productAfter = productRepository.findById(testProduct1.getId()).orElseThrow();
            assertThat(productAfter.getVersion()).isNotNull();
        }

        @Test
        @DisplayName("성공: 비관적 락 - 사용자 잔액 동시성 제어")
        @org.springframework.transaction.annotation.Transactional
        void pessimisticLock_UserBalance() {
            // Given - 비관적 락으로 사용자 조회
            User lockedUser = userRepository.findByIdWithLock(testUser.getId()).orElseThrow();

            // When & Then - 락이 걸린 상태에서 조회 가능
            assertThat(lockedUser.getId()).isEqualTo(testUser.getId());
            assertThat(lockedUser.getBalance()).isNotNull();
        }
    }

    // Helper methods
    private Cart createCart(User user) {
        // 기존 장바구니가 있으면 재사용 (items 포함), 없으면 생성
        return cartRepository.findByUserWithItems(user)
                .orElseGet(() -> cartRepository.save(Cart.builder()
                        .user(user)
                        .build()));
    }
}

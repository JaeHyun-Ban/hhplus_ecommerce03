package com.hhplus.ecommerce.order.application;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.cart.domain.Cart;
import com.hhplus.ecommerce.cart.domain.CartItem;
import com.hhplus.ecommerce.coupon.domain.Coupon;
import com.hhplus.ecommerce.coupon.domain.CouponStatus;
import com.hhplus.ecommerce.coupon.domain.CouponType;
import com.hhplus.ecommerce.coupon.domain.UserCoupon;
import com.hhplus.ecommerce.coupon.domain.UserCouponStatus;
import com.hhplus.ecommerce.order.domain.Order;
import com.hhplus.ecommerce.order.domain.OrderStatus;
import com.hhplus.ecommerce.product.domain.Category;
import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.domain.ProductStatus;
import com.hhplus.ecommerce.user.domain.User;
import com.hhplus.ecommerce.user.domain.UserRole;
import com.hhplus.ecommerce.user.domain.UserStatus;
import com.hhplus.ecommerce.cart.infrastructure.persistence.CartItemRepository;
import com.hhplus.ecommerce.cart.infrastructure.persistence.CartRepository;
import com.hhplus.ecommerce.coupon.infrastructure.persistence.CouponRepository;
import com.hhplus.ecommerce.coupon.infrastructure.persistence.UserCouponRepository;
import com.hhplus.ecommerce.order.infrastructure.persistence.OrderRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.CategoryRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.StockHistoryRepository;
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
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * OrderService 통합 테스트 - 주문 생성 (TestContainers 사용)
 *
 * 테스트 전략:
 * - 실제 MySQL 컨테이너를 사용한 통합 테스트
 * - UC-012: 주문 생성 및 결제 기능 검증
 */
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("OrderService 통합 테스트 - 주문 생성")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class OrderCreateIntegrationTest {

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
    private CartItemRepository cartItemRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private BalanceHistoryRepository balanceHistoryRepository;

    @Autowired
    private StockHistoryRepository stockHistoryRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private User testUser;
    private Category testCategory;
    private Product testProduct1;
    private Product testProduct2;
    private Cart testCart;
    private Coupon testCoupon;
    private UserCoupon testUserCoupon;

    @BeforeEach
    void setUp() {
        // 각 테스트 전에 DB 초기화 (외래키 순서 고려)
        stockHistoryRepository.deleteAll();
        balanceHistoryRepository.deleteAll();
        orderRepository.deleteAll(); // OrderCoupon은 cascade로 함께 삭제
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트용 카테고리 생성
        testCategory = createAndSaveCategory("전자제품", "전자제품 카테고리");

        // 테스트용 사용자 생성 (충분한 잔액)
        testUser = createAndSaveUser("test@test.com", "테스트사용자", BigDecimal.valueOf(500000));

        // 테스트용 상품 생성
        testProduct1 = createAndSaveProduct("노트북", 50, ProductStatus.AVAILABLE, BigDecimal.valueOf(100000));
        testProduct2 = createAndSaveProduct("마우스", 100, ProductStatus.AVAILABLE, BigDecimal.valueOf(20000));

        // 테스트용 장바구니 생성
        testCart = createAndSaveCart(testUser);

        // 장바구니에 상품 추가
        createAndSaveCartItem(testCart, testProduct1, 2); // 노트북 2개: 200,000원
        createAndSaveCartItem(testCart, testProduct2, 1); // 마우스 1개: 20,000원
        // 총 220,000원

        // 테스트용 쿠폰 생성 (10% 할인)
        testCoupon = createAndSaveCoupon("WELCOME10", CouponType.PERCENTAGE, BigDecimal.TEN);
        testUserCoupon = createAndSaveUserCoupon(testUser, testCoupon);
    }

    @Nested
    @DisplayName("주문 생성 테스트 (UC-012)")
    class CreateOrderTest {

        @Test
        @DisplayName("성공: 쿠폰 없이 주문 생성")
        void createOrder_WithoutCoupon_Success() {
            // Given
            Long userId = testUser.getId();
            String idempotencyKey = UUID.randomUUID().toString();

            // When
            Order result = orderService.createOrder(userId, null, idempotencyKey);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUser().getId()).isEqualTo(userId);
            assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(220000));
            assertThat(result.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getFinalAmount()).isEqualByComparingTo(BigDecimal.valueOf(220000));
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(result.getOrderNumber()).startsWith("ORD-");

            // 실제 DB에서 확인
            Optional<Order> savedOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
            assertThat(savedOrder).isPresent();

            // 잔액 이력 확인
            assertThat(balanceHistoryRepository.findAll()).hasSize(1);

            // 재고 이력 확인 (2개 상품)
            assertThat(stockHistoryRepository.findAll()).hasSize(2);

            // 장바구니가 비워졌는지 확인
            Cart cart = cartRepository.findByUser(testUser).orElseThrow();
            assertThat(cartItemRepository.findByCart(cart)).isEmpty();

            // 재고 확인
            Product product1 = productRepository.findById(testProduct1.getId()).orElseThrow();
            Product product2 = productRepository.findById(testProduct2.getId()).orElseThrow();
            assertThat(product1.getStock()).isEqualTo(48); // 50 - 2
            assertThat(product2.getStock()).isEqualTo(99); // 100 - 1

            // 잔액 확인
            User user = userRepository.findById(testUser.getId()).orElseThrow();
            assertThat(user.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(280000)); // 500000 - 220000
        }

        @Test
        @DisplayName("성공: 쿠폰 적용하여 주문 생성")
        void createOrder_WithCoupon_Success() {
            // Given
            Long userId = testUser.getId();
            Long userCouponId = testUserCoupon.getId();
            String idempotencyKey = UUID.randomUUID().toString();

            // When
            Order result = orderService.createOrder(userId, userCouponId, idempotencyKey);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(220000));
            assertThat(result.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(22000)); // 10% 할인
            assertThat(result.getFinalAmount()).isEqualByComparingTo(BigDecimal.valueOf(198000)); // 220000 - 22000
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);

            // 쿠폰이 사용됨으로 변경되었는지 확인
            UserCoupon usedCoupon = userCouponRepository.findById(userCouponId).orElseThrow();
            assertThat(usedCoupon.getStatus()).isEqualTo(UserCouponStatus.USED);
            assertThat(usedCoupon.getUsedAt()).isNotNull();

            // 잔액 확인
            User user = userRepository.findById(testUser.getId()).orElseThrow();
            assertThat(user.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(302000)); // 500000 - 198000
        }

        @Test
        @DisplayName("성공: 멱등성 키 중복 시 기존 주문 반환")
        void createOrder_DuplicateIdempotencyKey_ReturnsExistingOrder() {
            // Given
            Long userId = testUser.getId();
            String idempotencyKey = UUID.randomUUID().toString();

            // 첫 번째 주문 생성
            Order firstOrder = orderService.createOrder(userId, null, idempotencyKey);

            // 장바구니 다시 채우기 (멱등성 테스트를 위해)
            createAndSaveCartItem(testCart, testProduct1, 1);

            // When - 같은 멱등성 키로 두 번째 주문 시도
            Order secondOrder = orderService.createOrder(userId, null, idempotencyKey);

            // Then - 기존 주문이 반환되어야 함
            assertThat(secondOrder.getId()).isEqualTo(firstOrder.getId());
            assertThat(secondOrder.getOrderNumber()).isEqualTo(firstOrder.getOrderNumber());

            // DB에서 주문이 하나만 생성되었는지 확인
            assertThat(orderRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("성공: 여러 상품 주문")
        void createOrder_MultipleProducts_Success() {
            // Given
            Long userId = testUser.getId();
            String idempotencyKey = UUID.randomUUID().toString();

            // When
            Order result = orderService.createOrder(userId, null, idempotencyKey);

            // Then
            assertThat(result.getOrderItems()).hasSize(2);
            assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(220000));

            // 재고 이력이 2개 생성되었는지 확인
            assertThat(stockHistoryRepository.findAll()).hasSize(2);
        }

        @Test
        @DisplayName("실패: 사용자를 찾을 수 없음")
        void createOrder_UserNotFound() {
            // Given
            Long nonExistentUserId = 999L;
            String idempotencyKey = UUID.randomUUID().toString();

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(nonExistentUserId, null, idempotencyKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("사용자를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("실패: 장바구니가 비어있음")
        void createOrder_EmptyCart() {
            // Given
            Long userId = testUser.getId();
            String idempotencyKey = UUID.randomUUID().toString();

            // 장바구니 비우기
            Cart cart = cartRepository.findByUser(testUser).orElseThrow();
            cartItemRepository.findByCart(cart).forEach(cartItemRepository::delete);

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(userId, null, idempotencyKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("장바구니가 비어있습니다");
        }

        @Test
        @DisplayName("실패: 재고 부족")
        void createOrder_InsufficientStock() {
            // Given
            Long userId = testUser.getId();
            String idempotencyKey = UUID.randomUUID().toString();

            // 재고를 부족하게 만듦
            Product product = productRepository.findById(testProduct1.getId()).orElseThrow();
            product.decreaseStock(49); // 재고를 1로 만듦 (주문 수량은 2)
            productRepository.save(product);

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(userId, null, idempotencyKey))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("재고가 부족합니다");
        }

        @Test
        @DisplayName("실패: 잔액 부족")
        void createOrder_InsufficientBalance() {
            // Given
            User poorUser = createAndSaveUser("poor@test.com", "가난한사용자", BigDecimal.valueOf(10000));
            Cart poorCart = createAndSaveCart(poorUser);
            createAndSaveCartItem(poorCart, testProduct1, 2); // 200,000원 (잔액 10,000원)

            Long userId = poorUser.getId();
            String idempotencyKey = UUID.randomUUID().toString();

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(userId, null, idempotencyKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("잔액이 부족합니다");
        }

        @Test
        @DisplayName("실패: 쿠폰을 찾을 수 없음")
        void createOrder_CouponNotFound() {
            // Given
            Long userId = testUser.getId();
            Long nonExistentCouponId = 999L;
            String idempotencyKey = UUID.randomUUID().toString();

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(userId, nonExistentCouponId, idempotencyKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("쿠폰을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("실패: 다른 사용자의 쿠폰 사용 시도")
        void createOrder_OtherUserCoupon() {
            // Given
            User otherUser = createAndSaveUser("other@test.com", "다른사용자", BigDecimal.valueOf(500000));
            UserCoupon otherUserCoupon = createAndSaveUserCoupon(otherUser, testCoupon);

            Long userId = testUser.getId();
            Long otherUserCouponId = otherUserCoupon.getId();
            String idempotencyKey = UUID.randomUUID().toString();

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(userId, otherUserCouponId, idempotencyKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("본인의 쿠폰만 사용할 수 있습니다");
        }

        @Test
        @DisplayName("실패: 이미 사용된 쿠폰")
        void createOrder_AlreadyUsedCoupon() {
            // Given
            // 쿠폰을 먼저 사용하는 주문 생성
            Long userId = testUser.getId();
            String firstIdempotencyKey = UUID.randomUUID().toString();
            orderService.createOrder(userId, testUserCoupon.getId(), firstIdempotencyKey);

            // 다른 주문을 위해 장바구니 다시 채우기
            createAndSaveCartItem(testCart, testProduct1, 1);

            String secondIdempotencyKey = UUID.randomUUID().toString();

            // When & Then - 같은 쿠폰을 재사용하려고 시도
            assertThatThrownBy(() -> orderService.createOrder(userId, testUserCoupon.getId(), secondIdempotencyKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("사용할 수 없는 쿠폰입니다");
        }

        @Test
        @DisplayName("실패: 최소 주문 금액 미달")
        void createOrder_MinimumOrderAmountNotMet() {
            // Given
            // 최소 주문 금액 300,000원인 쿠폰을 처음부터 생성
            Coupon highMinCoupon = Coupon.builder()
                    .code("HIGH100")
                    .name("HIGH100 쿠폰")
                    .type(CouponType.FIXED_AMOUNT)
                    .discountValue(BigDecimal.valueOf(10000))
                    .minimumOrderAmount(BigDecimal.valueOf(300000)) // 최소 주문 금액 설정
                    .totalQuantity(100)
                    .issuedQuantity(0)
                    .maxIssuePerUser(1)
                    .issueStartAt(LocalDateTime.now().minusDays(1))
                    .issueEndAt(LocalDateTime.now().plusDays(30))
                    .validFrom(LocalDateTime.now())
                    .validUntil(LocalDateTime.now().plusDays(60))
                    .status(CouponStatus.ACTIVE)
                    .version(0L)
                    .build();
            highMinCoupon = couponRepository.save(highMinCoupon);
            UserCoupon highMinUserCoupon = createAndSaveUserCoupon(testUser, highMinCoupon);

            Long userId = testUser.getId();
            Long userCouponId = highMinUserCoupon.getId();
            String idempotencyKey = UUID.randomUUID().toString();

            // When & Then (장바구니 총액은 220,000원)
            assertThatThrownBy(() -> orderService.createOrder(userId, userCouponId, idempotencyKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("최소 주문 금액");
        }
    }

    // ========================================
    // 테스트 데이터 생성 헬퍼 메서드
    // ========================================

    private Category createAndSaveCategory(String name, String description) {
        Category category = Category.builder()
                .name(name)
                .description(description)
                .build();
        return categoryRepository.save(category);
    }

    private User createAndSaveUser(String email, String name, BigDecimal balance) {
        User user = User.builder()
                .email(email)
                .password("password123")
                .name(name)
                .balance(balance)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        return userRepository.save(user);
    }

    private Product createAndSaveProduct(String name, int stock, ProductStatus status, BigDecimal price) {
        Product product = Product.builder()
                .name(name)
                .description(name + " 설명")
                .price(price)
                .stock(stock)
                .safetyStock(10)
                .category(testCategory)
                .status(status)
                .version(0L)
                .build();
        return productRepository.save(product);
    }

    private Cart createAndSaveCart(User user) {
        Cart cart = Cart.builder()
                .user(user)
                .build();
        return cartRepository.save(cart);
    }

    private CartItem createAndSaveCartItem(Cart cart, Product product, Integer quantity) {
        CartItem cartItem = CartItem.builder()
                .cart(cart)
                .product(product)
                .quantity(quantity)
                .priceAtAdd(product.getPrice())
                .build();
        return cartItemRepository.save(cartItem);
    }

    private Coupon createAndSaveCoupon(String code, CouponType type, BigDecimal discountValue) {
        Coupon coupon = Coupon.builder()
                .code(code)
                .name(code + " 쿠폰")
                .type(type)
                .discountValue(discountValue)
                .minimumOrderAmount(BigDecimal.ZERO)
                .totalQuantity(100)
                .issuedQuantity(0)
                .maxIssuePerUser(1)
                .issueStartAt(LocalDateTime.now().minusDays(1))
                .issueEndAt(LocalDateTime.now().plusDays(30))
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(60))
                .status(CouponStatus.ACTIVE)
                .version(0L)
                .build();
        return couponRepository.save(coupon);
    }

    private UserCoupon createAndSaveUserCoupon(User user, Coupon coupon) {
        UserCoupon userCoupon = UserCoupon.builder()
                .user(user)
                .coupon(coupon)
                .status(UserCouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .build();
        return userCouponRepository.save(userCoupon);
    }
}

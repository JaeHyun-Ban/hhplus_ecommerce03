package com.hhplus.ecommerce.application.order;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.domain.cart.Cart;
import com.hhplus.ecommerce.domain.cart.CartItem;
import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderStatus;
import com.hhplus.ecommerce.domain.product.Category;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductStatus;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRole;
import com.hhplus.ecommerce.domain.user.UserStatus;
import com.hhplus.ecommerce.infrastructure.persistence.cart.CartItemRepository;
import com.hhplus.ecommerce.infrastructure.persistence.cart.CartRepository;
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
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * OrderService 통합 테스트 - 주문 조회 (TestContainers 사용)
 *
 * 테스트 전략:
 * - 실제 MySQL 컨테이너를 사용한 통합 테스트
 * - UC-013: 주문 상세 조회 기능 검증
 * - UC-014: 주문 목록 조회 기능 검증
 */
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("OrderService 통합 테스트 - 주문 조회")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class OrderQueryIntegrationTest {

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

    @BeforeEach
    void setUp() {
        // 각 테스트 전에 DB 초기화 (외래키 순서 고려)
        stockHistoryRepository.deleteAll();
        balanceHistoryRepository.deleteAll();
        orderRepository.deleteAll(); // OrderCoupon은 cascade로 함께 삭제
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트용 카테고리 생성
        testCategory = createAndSaveCategory("전자제품", "전자제품 카테고리");

        // 테스트용 사용자 생성
        testUser = createAndSaveUser("test@test.com", "테스트사용자", BigDecimal.valueOf(1000000));

        // 테스트용 상품 생성
        testProduct1 = createAndSaveProduct("노트북", 50, ProductStatus.AVAILABLE, BigDecimal.valueOf(100000));
        testProduct2 = createAndSaveProduct("마우스", 100, ProductStatus.AVAILABLE, BigDecimal.valueOf(20000));

        // 테스트용 장바구니 생성
        testCart = createAndSaveCart(testUser);
    }

    @Nested
    @DisplayName("주문 상세 조회 테스트 (UC-013)")
    class GetOrderTest {

        @Test
        @DisplayName("성공: 주문 ID로 상세 조회")
        void getOrder_ById_Success() {
            // Given
            // 장바구니에 상품 추가 및 주문 생성
            createAndSaveCartItem(testCart, testProduct1, 2);
            String idempotencyKey = UUID.randomUUID().toString();
            Order createdOrder = orderService.createOrder(testUser.getId(), null, idempotencyKey);
            Long orderId = createdOrder.getId();

            // When
            Order result = orderService.getOrder(orderId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(orderId);
            assertThat(result.getUser().getId()).isEqualTo(testUser.getId());
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(result.getOrderNumber()).isNotNull();
            assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(200000));
            assertThat(result.getFinalAmount()).isEqualByComparingTo(BigDecimal.valueOf(200000));
        }

        @Test
        @DisplayName("성공: 주문 번호로 조회")
        void getOrderByNumber_Success() {
            // Given
            // 장바구니에 상품 추가 및 주문 생성
            createAndSaveCartItem(testCart, testProduct1, 1);
            String idempotencyKey = UUID.randomUUID().toString();
            Order createdOrder = orderService.createOrder(testUser.getId(), null, idempotencyKey);
            String orderNumber = createdOrder.getOrderNumber();

            // When
            Order result = orderService.getOrderByNumber(orderNumber);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getOrderNumber()).isEqualTo(orderNumber);
            assertThat(result.getUser().getId()).isEqualTo(testUser.getId());
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("성공: 주문 상세 정보 확인 (주문 항목 포함)")
        void getOrder_WithOrderItems_Success() {
            // Given
            // 장바구니에 여러 상품 추가
            createAndSaveCartItem(testCart, testProduct1, 2);
            createAndSaveCartItem(testCart, testProduct2, 3);
            String idempotencyKey = UUID.randomUUID().toString();
            Order createdOrder = orderService.createOrder(testUser.getId(), null, idempotencyKey);
            Long orderId = createdOrder.getId();

            // When
            Order result = orderService.getOrder(orderId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getOrderItems()).isNotEmpty();
            assertThat(result.getOrderItems()).hasSize(2);
            assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(260000)); // 200000 + 60000
        }

        @Test
        @DisplayName("실패: 주문을 찾을 수 없음")
        void getOrder_NotFound() {
            // Given
            Long nonExistentOrderId = 999L;

            // When & Then
            assertThatThrownBy(() -> orderService.getOrder(nonExistentOrderId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("주문을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 주문 번호로 조회")
        void getOrderByNumber_NotFound() {
            // Given
            String nonExistentOrderNumber = "ORD-99999999-999999";

            // When & Then
            assertThatThrownBy(() -> orderService.getOrderByNumber(nonExistentOrderNumber))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("주문을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("사용자별 주문 목록 조회 테스트 (UC-014)")
    class GetUserOrdersTest {

        @Test
        @DisplayName("성공: 사용자의 주문 목록 조회")
        void getUserOrders_Success() {
            // Given
            Long userId = testUser.getId();
            Pageable pageable = PageRequest.of(0, 10);

            // 여러 주문 생성
            createAndSaveCartItem(testCart, testProduct1, 1);
            orderService.createOrder(userId, null, UUID.randomUUID().toString());

            // 장바구니 다시 채우기
            createAndSaveCartItem(testCart, testProduct2, 2);
            orderService.createOrder(userId, null, UUID.randomUUID().toString());

            // When
            Page<Order> result = orderService.getUserOrders(userId, pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent().get(0).getUser().getId()).isEqualTo(userId);
            assertThat(result.getContent().get(1).getUser().getId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("성공: 최신 주문순으로 정렬 확인")
        void getUserOrders_OrderedByLatest() throws InterruptedException {
            // Given
            Long userId = testUser.getId();
            Pageable pageable = PageRequest.of(0, 10);

            // 첫 번째 주문
            createAndSaveCartItem(testCart, testProduct1, 1);
            Order firstOrder = orderService.createOrder(userId, null, UUID.randomUUID().toString());

            // 시간 차이를 위한 대기
            Thread.sleep(100);

            // 두 번째 주문
            createAndSaveCartItem(testCart, testProduct2, 1);
            Order secondOrder = orderService.createOrder(userId, null, UUID.randomUUID().toString());

            // When
            Page<Order> result = orderService.getUserOrders(userId, pageable);

            // Then
            assertThat(result.getContent()).hasSize(2);
            // 최신 주문이 먼저 와야 함
            assertThat(result.getContent().get(0).getId()).isEqualTo(secondOrder.getId());
            assertThat(result.getContent().get(1).getId()).isEqualTo(firstOrder.getId());
        }

        @Test
        @DisplayName("성공: 페이징 처리 확인")
        void getUserOrders_Paging() {
            // Given
            Long userId = testUser.getId();

            // 3개의 주문 생성
            for (int i = 0; i < 3; i++) {
                createAndSaveCartItem(testCart, testProduct1, 1);
                orderService.createOrder(userId, null, UUID.randomUUID().toString());
            }

            // 첫 번째 페이지 (크기 2)
            Pageable firstPage = PageRequest.of(0, 2);

            // When
            Page<Order> result = orderService.getUserOrders(userId, firstPage);

            // Then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getTotalPages()).isEqualTo(2);
            assertThat(result.hasNext()).isTrue();
        }

        @Test
        @DisplayName("성공: 주문이 없는 사용자")
        void getUserOrders_NoOrders() {
            // Given
            User newUser = createAndSaveUser("new@test.com", "새사용자", BigDecimal.valueOf(100000));
            Long userId = newUser.getId();
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Order> result = orderService.getUserOrders(userId, pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("성공: 여러 사용자의 주문 분리 확인")
        void getUserOrders_MultipleUsers() {
            // Given
            User user2 = createAndSaveUser("user2@test.com", "사용자2", BigDecimal.valueOf(500000));
            Cart cart2 = createAndSaveCart(user2);

            // 첫 번째 사용자 주문
            createAndSaveCartItem(testCart, testProduct1, 1);
            orderService.createOrder(testUser.getId(), null, UUID.randomUUID().toString());

            // 두 번째 사용자 주문
            createAndSaveCartItem(cart2, testProduct2, 1);
            orderService.createOrder(user2.getId(), null, UUID.randomUUID().toString());

            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Order> user1Orders = orderService.getUserOrders(testUser.getId(), pageable);
            Page<Order> user2Orders = orderService.getUserOrders(user2.getId(), pageable);

            // Then
            assertThat(user1Orders.getTotalElements()).isEqualTo(1);
            assertThat(user2Orders.getTotalElements()).isEqualTo(1);
            assertThat(user1Orders.getContent().get(0).getUser().getId()).isEqualTo(testUser.getId());
            assertThat(user2Orders.getContent().get(0).getUser().getId()).isEqualTo(user2.getId());
        }

        @Test
        @DisplayName("실패: 사용자를 찾을 수 없음")
        void getUserOrders_UserNotFound() {
            // Given
            Long nonExistentUserId = 999L;
            Pageable pageable = PageRequest.of(0, 10);

            // When & Then
            assertThatThrownBy(() -> orderService.getUserOrders(nonExistentUserId, pageable))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("사용자를 찾을 수 없습니다");
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
}

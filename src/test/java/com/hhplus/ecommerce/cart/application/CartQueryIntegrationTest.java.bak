package com.hhplus.ecommerce.cart.application;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.cart.domain.Cart;
import com.hhplus.ecommerce.cart.domain.CartItem;
import com.hhplus.ecommerce.product.domain.Category;
import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.domain.ProductStatus;
import com.hhplus.ecommerce.user.domain.User;
import com.hhplus.ecommerce.user.domain.UserRole;
import com.hhplus.ecommerce.user.domain.UserStatus;
import com.hhplus.ecommerce.cart.infrastructure.persistence.CartItemRepository;
import com.hhplus.ecommerce.cart.infrastructure.persistence.CartRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.CategoryRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * CartService 통합 테스트 - 장바구니 조회 (TestContainers 사용)
 *
 * 테스트 전략:
 * - 실제 MySQL 컨테이너를 사용한 통합 테스트
 * - UC-007: 장바구니 조회 기능 검증
 */
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("CartService 통합 테스트 - 장바구니 조회")
class CartQueryIntegrationTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private User testUser;
    private Cart testCart;
    private Product testProduct1;
    private Product testProduct2;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        // 각 테스트 전에 DB 초기화
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        // 테스트용 카테고리 생성
        testCategory = createAndSaveCategory("전자제품", "전자제품 카테고리");

        // 테스트용 사용자 생성
        testUser = createAndSaveUser("test@test.com", "테스트사용자");

        // 테스트용 상품 생성
        testProduct1 = createAndSaveProduct("노트북", 50, ProductStatus.AVAILABLE, BigDecimal.valueOf(1000000));
        testProduct2 = createAndSaveProduct("마우스", 100, ProductStatus.AVAILABLE, BigDecimal.valueOf(30000));

        // 테스트용 장바구니 생성
        testCart = createAndSaveCart(testUser);
    }

    @Nested
    @DisplayName("장바구니 조회 테스트")
    class GetCartTest {

        @Test
        @DisplayName("성공: 기존 장바구니 조회")
        void getCart_ExistingCart_Success() {
            // Given
            Long userId = testUser.getId();

            // When
            Cart result = cartService.getCart(userId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(testCart.getId());
            assertThat(result.getUser().getId()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("성공: 장바구니 없을 때 빈 장바구니 자동 생성")
        void getCart_CreateEmptyCart_Success() {
            // Given
            User newUser = createAndSaveUser("new@test.com", "새사용자");
            Long userId = newUser.getId();

            // When
            Cart result = cartService.getCart(userId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUser().getId()).isEqualTo(userId);

            // 실제 DB에서 확인
            Optional<Cart> savedCart = cartRepository.findByUser(newUser);
            assertThat(savedCart).isPresent();
            assertThat(savedCart.get().getUser().getId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("성공: 장바구니에 아이템이 있는 경우")
        void getCart_WithItems() {
            // Given
            Long userId = testUser.getId();

            // 장바구니에 아이템 추가
            CartItem item1 = createAndSaveCartItem(testCart, testProduct1, 2);
            CartItem item2 = createAndSaveCartItem(testCart, testProduct2, 3);

            // When
            Cart result = cartService.getCart(userId);

            // Then
            assertThat(result).isNotNull();
            List<CartItem> items = cartItemRepository.findByCart(result);
            assertThat(items).hasSize(2);
        }

        @Test
        @DisplayName("성공: 여러 사용자의 장바구니 각각 조회")
        void getCart_MultipleUsers() {
            // Given
            User user2 = createAndSaveUser("user2@test.com", "사용자2");
            User user3 = createAndSaveUser("user3@test.com", "사용자3");

            // When
            Cart cart1 = cartService.getCart(testUser.getId());
            Cart cart2 = cartService.getCart(user2.getId());
            Cart cart3 = cartService.getCart(user3.getId());

            // Then
            assertThat(cart1).isNotNull();
            assertThat(cart2).isNotNull();
            assertThat(cart3).isNotNull();
            assertThat(cart1.getId()).isNotEqualTo(cart2.getId());
            assertThat(cart2.getId()).isNotEqualTo(cart3.getId());
        }

        @Test
        @DisplayName("실패: 사용자를 찾을 수 없음")
        void getCart_UserNotFound() {
            // Given
            Long nonExistentUserId = 999L;

            // When & Then
            assertThatThrownBy(() -> cartService.getCart(nonExistentUserId))
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

    private User createAndSaveUser(String email, String name) {
        User user = User.builder()
                .email(email)
                .password("password123")
                .name(name)
                .balance(BigDecimal.valueOf(100000))
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

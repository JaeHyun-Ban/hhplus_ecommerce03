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
 * CartService 통합 테스트 - 장바구니 상품 추가 (TestContainers 사용)
 *
 * 테스트 전략:
 * - 실제 MySQL 컨테이너를 사용한 통합 테스트
 * - UC-008: 장바구니 상품 추가 기능 검증
 */
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("CartService 통합 테스트 - 장바구니 상품 추가")
class CartItemAddIntegrationTest {

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
    @DisplayName("장바구니 상품 추가 테스트")
    class AddToCartTest {

        @Test
        @DisplayName("성공: 새 상품 추가")
        void addToCart_NewProduct_Success() {
            // Given
            Long userId = testUser.getId();
            Long productId = testProduct1.getId();
            Integer quantity = 2;

            // When
            CartItem result = cartService.addToCart(userId, productId, quantity);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getProduct().getId()).isEqualTo(productId);
            assertThat(result.getQuantity()).isEqualTo(quantity);
            assertThat(result.getPriceAtAdd()).isEqualByComparingTo(testProduct1.getPrice());

            // 실제 DB에서 확인
            List<CartItem> items = cartItemRepository.findByCart(testCart);
            assertThat(items).hasSize(1);
            assertThat(items.get(0).getQuantity()).isEqualTo(quantity);
        }

        @Test
        @DisplayName("성공: 기존 상품 수량 증가")
        void addToCart_ExistingProduct_IncreaseQuantity() {
            // Given
            Long userId = testUser.getId();
            Long productId = testProduct1.getId();
            Integer initialQuantity = 2;
            Integer addQuantity = 3;

            // 기존 아이템 추가
            CartItem existingItem = createAndSaveCartItem(testCart, testProduct1, initialQuantity);

            // When
            CartItem result = cartService.addToCart(userId, productId, addQuantity);

            // Then
            assertThat(result.getQuantity()).isEqualTo(initialQuantity + addQuantity); // 2 + 3 = 5
            assertThat(result.getUpdatedAt()).isNotNull();

            // 실제 DB에서 확인 - 항목이 중복되지 않고 수량만 증가해야 함
            List<CartItem> items = cartItemRepository.findByCart(testCart);
            assertThat(items).hasSize(1);
        }

        @Test
        @DisplayName("성공: 장바구니 없을 때 자동 생성 후 추가")
        void addToCart_CreateCartIfNotExists() {
            // Given
            User newUser = createAndSaveUser("new@test.com", "새사용자");
            Long userId = newUser.getId();
            Long productId = testProduct1.getId();
            Integer quantity = 1;

            // When
            CartItem result = cartService.addToCart(userId, productId, quantity);

            // Then
            assertThat(result).isNotNull();

            // 장바구니 생성 확인
            Optional<Cart> newCart = cartRepository.findByUser(newUser);
            assertThat(newCart).isPresent();

            // 아이템 추가 확인
            List<CartItem> items = cartItemRepository.findByCart(newCart.get());
            assertThat(items).hasSize(1);
        }

        @Test
        @DisplayName("성공: 여러 상품 추가")
        void addToCart_MultipleProducts() {
            // Given
            Long userId = testUser.getId();

            // When
            cartService.addToCart(userId, testProduct1.getId(), 2);
            cartService.addToCart(userId, testProduct2.getId(), 3);

            // Then
            List<CartItem> items = cartItemRepository.findByCart(testCart);
            assertThat(items).hasSize(2);
        }

        @Test
        @DisplayName("성공: 추가 시 현재 가격 저장 확인")
        void addToCart_PriceAtAddCheck() {
            // Given
            Long userId = testUser.getId();
            Long productId = testProduct1.getId();
            Integer quantity = 1;
            BigDecimal currentPrice = testProduct1.getPrice();

            // When
            CartItem result = cartService.addToCart(userId, productId, quantity);

            // Then
            assertThat(result.getPriceAtAdd()).isEqualByComparingTo(currentPrice);
        }

        @Test
        @DisplayName("실패: 수량이 0 이하")
        void addToCart_InvalidQuantity_Zero() {
            // Given
            Long userId = testUser.getId();
            Long productId = testProduct1.getId();
            Integer quantity = 0;

            // When & Then
            assertThatThrownBy(() -> cartService.addToCart(userId, productId, quantity))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("수량은 1 이상이어야 합니다");
        }

        @Test
        @DisplayName("실패: 수량이 음수")
        void addToCart_InvalidQuantity_Negative() {
            // Given
            Long userId = testUser.getId();
            Long productId = testProduct1.getId();
            Integer quantity = -1;

            // When & Then
            assertThatThrownBy(() -> cartService.addToCart(userId, productId, quantity))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("수량은 1 이상이어야 합니다");
        }

        @Test
        @DisplayName("실패: 사용자를 찾을 수 없음")
        void addToCart_UserNotFound() {
            // Given
            Long nonExistentUserId = 999L;
            Long productId = testProduct1.getId();
            Integer quantity = 1;

            // When & Then
            assertThatThrownBy(() -> cartService.addToCart(nonExistentUserId, productId, quantity))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("사용자를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("실패: 상품을 찾을 수 없음")
        void addToCart_ProductNotFound() {
            // Given
            Long userId = testUser.getId();
            Long nonExistentProductId = 999L;
            Integer quantity = 1;

            // When & Then
            assertThatThrownBy(() -> cartService.addToCart(userId, nonExistentProductId, quantity))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("상품을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("실패: 판매 중인 상품이 아님")
        void addToCart_ProductNotAvailable() {
            // Given
            Long userId = testUser.getId();
            Product unavailableProduct = createAndSaveProduct("품절상품", 0, ProductStatus.OUT_OF_STOCK, BigDecimal.valueOf(10000));
            Long productId = unavailableProduct.getId();
            Integer quantity = 1;

            // When & Then
            assertThatThrownBy(() -> cartService.addToCart(userId, productId, quantity))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("판매 중인 상품이 아닙니다");
        }

        @Test
        @DisplayName("실패: 재고 부족")
        void addToCart_InsufficientStock() {
            // Given
            Long userId = testUser.getId();
            Long productId = testProduct1.getId(); // stock: 50
            Integer quantity = 100; // 재고보다 많음

            // When & Then
            assertThatThrownBy(() -> cartService.addToCart(userId, productId, quantity))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("재고가 부족합니다");
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

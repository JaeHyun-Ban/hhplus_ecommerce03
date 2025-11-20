package com.hhplus.ecommerce.application.cart;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.domain.cart.Cart;
import com.hhplus.ecommerce.domain.cart.CartItem;
import com.hhplus.ecommerce.domain.product.Category;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductStatus;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRole;
import com.hhplus.ecommerce.domain.user.UserStatus;
import com.hhplus.ecommerce.infrastructure.persistence.cart.CartItemRepository;
import com.hhplus.ecommerce.infrastructure.persistence.cart.CartRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.CategoryRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.ProductRepository;
import com.hhplus.ecommerce.infrastructure.persistence.user.UserRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * CartService 통합 테스트 - 장바구니 관리 (TestContainers 사용)
 *
 * 테스트 전략:
 * - 실제 MySQL 컨테이너를 사용한 통합 테스트
 * - UC-009: 장바구니 수량 변경 기능 검증
 * - UC-010: 장바구니 항목 삭제 기능 검증
 */
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("CartService 통합 테스트 - 장바구니 관리")
class CartItemManageIntegrationTest {

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
    @DisplayName("장바구니 수량 변경 테스트")
    class UpdateCartItemQuantityTest {

        @Test
        @DisplayName("성공: 수량 증가")
        void updateCartItemQuantity_Increase_Success() {
            // Given
            Integer initialQuantity = 2;
            Integer newQuantity = 5;
            CartItem cartItem = createAndSaveCartItem(testCart, testProduct1, initialQuantity);
            Long cartItemId = cartItem.getId();

            // When
            cartService.updateCartItemQuantity(cartItemId, newQuantity);

            // Then
            CartItem updated = cartItemRepository.findById(cartItemId).orElseThrow();
            assertThat(updated.getQuantity()).isEqualTo(newQuantity);
            assertThat(updated.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("성공: 수량 감소")
        void updateCartItemQuantity_Decrease_Success() {
            // Given
            Integer initialQuantity = 10;
            Integer newQuantity = 3;
            CartItem cartItem = createAndSaveCartItem(testCart, testProduct1, initialQuantity);
            Long cartItemId = cartItem.getId();

            // When
            cartService.updateCartItemQuantity(cartItemId, newQuantity);

            // Then
            CartItem updated = cartItemRepository.findById(cartItemId).orElseThrow();
            assertThat(updated.getQuantity()).isEqualTo(newQuantity);
        }

        @Test
        @DisplayName("성공: 수량을 1로 변경")
        void updateCartItemQuantity_ToOne() {
            // Given
            Integer initialQuantity = 5;
            Integer newQuantity = 1;
            CartItem cartItem = createAndSaveCartItem(testCart, testProduct1, initialQuantity);
            Long cartItemId = cartItem.getId();

            // When
            cartService.updateCartItemQuantity(cartItemId, newQuantity);

            // Then
            CartItem updated = cartItemRepository.findById(cartItemId).orElseThrow();
            assertThat(updated.getQuantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("실패: 수량이 0 이하")
        void updateCartItemQuantity_InvalidQuantity_Zero() {
            // Given
            CartItem cartItem = createAndSaveCartItem(testCart, testProduct1, 5);
            Long cartItemId = cartItem.getId();
            Integer newQuantity = 0;

            // When & Then
            assertThatThrownBy(() -> cartService.updateCartItemQuantity(cartItemId, newQuantity))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("수량은 1 이상이어야 합니다");
        }

        @Test
        @DisplayName("실패: 수량이 음수")
        void updateCartItemQuantity_InvalidQuantity_Negative() {
            // Given
            CartItem cartItem = createAndSaveCartItem(testCart, testProduct1, 5);
            Long cartItemId = cartItem.getId();
            Integer newQuantity = -1;

            // When & Then
            assertThatThrownBy(() -> cartService.updateCartItemQuantity(cartItemId, newQuantity))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("수량은 1 이상이어야 합니다");
        }

        @Test
        @DisplayName("실패: 장바구니 항목을 찾을 수 없음")
        void updateCartItemQuantity_CartItemNotFound() {
            // Given
            Long nonExistentCartItemId = 999L;
            Integer newQuantity = 3;

            // When & Then
            assertThatThrownBy(() -> cartService.updateCartItemQuantity(nonExistentCartItemId, newQuantity))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("장바구니 항목을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("실패: 재고 부족")
        void updateCartItemQuantity_InsufficientStock() {
            // Given
            CartItem cartItem = createAndSaveCartItem(testCart, testProduct1, 2);
            Long cartItemId = cartItem.getId();
            Integer newQuantity = 100; // 재고(50)보다 많음

            // When & Then
            assertThatThrownBy(() -> cartService.updateCartItemQuantity(cartItemId, newQuantity))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("재고가 부족합니다");
        }

        @Test
        @DisplayName("성공: 재고 한계까지 변경 가능")
        void updateCartItemQuantity_ToMaxStock() {
            // Given
            CartItem cartItem = createAndSaveCartItem(testCart, testProduct1, 2);
            Long cartItemId = cartItem.getId();
            Integer newQuantity = 50; // 재고 전체

            // When
            cartService.updateCartItemQuantity(cartItemId, newQuantity);

            // Then
            CartItem updated = cartItemRepository.findById(cartItemId).orElseThrow();
            assertThat(updated.getQuantity()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("장바구니 항목 삭제 테스트")
    class RemoveCartItemTest {

        @Test
        @DisplayName("성공: 장바구니 항목 삭제")
        void removeCartItem_Success() {
            // Given
            CartItem cartItem = createAndSaveCartItem(testCart, testProduct1, 2);
            Long cartItemId = cartItem.getId();

            // When
            cartService.removeCartItem(cartItemId);

            // Then
            Optional<CartItem> deleted = cartItemRepository.findById(cartItemId);
            assertThat(deleted).isEmpty();
        }

        @Test
        @DisplayName("성공: 여러 항목 중 하나만 삭제")
        void removeCartItem_OneOfMultiple() {
            // Given
            CartItem cartItem1 = createAndSaveCartItem(testCart, testProduct1, 2);
            CartItem cartItem2 = createAndSaveCartItem(testCart, testProduct2, 3);
            Long cartItemId1 = cartItem1.getId();

            // When
            cartService.removeCartItem(cartItemId1);

            // Then
            Optional<CartItem> deleted = cartItemRepository.findById(cartItemId1);
            Optional<CartItem> remaining = cartItemRepository.findById(cartItem2.getId());

            assertThat(deleted).isEmpty();
            assertThat(remaining).isPresent();
        }

        @Test
        @DisplayName("성공: 모든 항목 개별 삭제")
        void removeCartItem_All() {
            // Given
            CartItem cartItem1 = createAndSaveCartItem(testCart, testProduct1, 2);
            CartItem cartItem2 = createAndSaveCartItem(testCart, testProduct2, 3);

            // When
            cartService.removeCartItem(cartItem1.getId());
            cartService.removeCartItem(cartItem2.getId());

            // Then
            assertThat(cartItemRepository.findByCart(testCart)).isEmpty();
        }

        @Test
        @DisplayName("실패: 장바구니 항목을 찾을 수 없음")
        void removeCartItem_CartItemNotFound() {
            // Given
            Long nonExistentCartItemId = 999L;

            // When & Then
            assertThatThrownBy(() -> cartService.removeCartItem(nonExistentCartItemId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("장바구니 항목을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("실패: 이미 삭제된 항목 재삭제 시도")
        void removeCartItem_AlreadyDeleted() {
            // Given
            CartItem cartItem = createAndSaveCartItem(testCart, testProduct1, 2);
            Long cartItemId = cartItem.getId();

            // 먼저 삭제
            cartService.removeCartItem(cartItemId);

            // When & Then
            assertThatThrownBy(() -> cartService.removeCartItem(cartItemId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("장바구니 항목을 찾을 수 없습니다");
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

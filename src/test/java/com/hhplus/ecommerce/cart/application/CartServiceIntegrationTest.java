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

import static org.assertj.core.api.Assertions.*;

/**
 * CartService 통합 테스트 (TestContainers 사용)
 *
 * 테스트 전략:
 * - 실제 MySQL 컨테이너를 사용한 통합 테스트
 * - JPA, 트랜잭션, DB 제약조건 등 실제 동작 검증
 * - 장바구니 도메인 로직 검증
 */
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("CartService 통합 테스트 (TestContainers)")
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
class CartServiceIntegrationTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private User testUser;
    private Product testProduct1;
    private Product testProduct2;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        // 각 테스트 전에 DB 초기화
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        setupTestData();
    }

    private void setupTestData() {
        // 테스트 사용자 생성
        String uniqueEmail = "test_" + System.currentTimeMillis() + "@example.com";
        testUser = userRepository.save(User.builder()
                .email(uniqueEmail)
                .password("password123")
                .name("테스트사용자")
                .balance(BigDecimal.valueOf(100000))
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());

        // 테스트 카테고리 생성
        String uniqueCategoryName = "테스트카테고리_" + System.currentTimeMillis();
        testCategory = categoryRepository.save(Category.builder()
                .name(uniqueCategoryName)
                .build());

        // 테스트 상품 생성
        testProduct1 = productRepository.save(Product.builder()
                .name("테스트상품1_" + System.currentTimeMillis())
                .price(BigDecimal.valueOf(10000))
                .stock(100)
                .safetyStock(10)
                .status(ProductStatus.AVAILABLE)
                .category(testCategory)
                .build());

        testProduct2 = productRepository.save(Product.builder()
                .name("테스트상품2_" + System.currentTimeMillis())
                .price(BigDecimal.valueOf(20000))
                .stock(50)
                .safetyStock(5)
                .status(ProductStatus.AVAILABLE)
                .category(testCategory)
                .build());
    }

    @Nested
    @DisplayName("UC-007: 장바구니 조회 통합 테스트")
    class GetCartIntegrationTest {

        @Test
        @DisplayName("성공: 빈 장바구니 조회 (자동 생성)")
        void getCart_Success_EmptyCart() {
            // Given
            Long userId = testUser.getId();

            // When
            Cart result = cartService.getCart(userId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getUser().getId()).isEqualTo(userId);
            assertThat(result.getItems()).isEmpty();

            // DB에 저장 확인
            Cart savedCart = cartRepository.findByUser(testUser).orElseThrow();
            assertThat(savedCart.getId()).isEqualTo(result.getId());
        }

        @Test
        @DisplayName("성공: 상품이 있는 장바구니 조회")
        void getCart_Success_WithItems() {
            // Given - 장바구니에 상품 추가
            cartService.addToCart(testUser.getId(), testProduct1.getId(), 2);
            cartService.addToCart(testUser.getId(), testProduct2.getId(), 1);

            // When
            Cart result = cartService.getCart(testUser.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getItems()).hasSize(2);

            // 항목 내용 확인
            CartItem item1 = result.getItems().stream()
                    .filter(item -> item.getProduct().getId().equals(testProduct1.getId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(item1.getQuantity()).isEqualTo(2);
            assertThat(item1.getPriceAtAdd()).isEqualByComparingTo(testProduct1.getPrice());

            CartItem item2 = result.getItems().stream()
                    .filter(item -> item.getProduct().getId().equals(testProduct2.getId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(item2.getQuantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 사용자")
        void getCart_Fail_UserNotFound() {
            // Given
            Long nonExistentUserId = 99999L;

            // When & Then
            assertThatThrownBy(() ->
                    cartService.getCart(nonExistentUserId)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("사용자를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("UC-008: 장바구니 상품 추가 통합 테스트")
    class AddToCartIntegrationTest {

        @Test
        @DisplayName("성공: 새 상품 추가")
        void addToCart_Success_NewItem() {
            // Given
            Long userId = testUser.getId();
            Long productId = testProduct1.getId();
            int quantity = 3;

            // When
            CartItem result = cartService.addToCart(userId, productId, quantity);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getProduct().getId()).isEqualTo(productId);
            assertThat(result.getQuantity()).isEqualTo(quantity);
            assertThat(result.getPriceAtAdd()).isEqualByComparingTo(testProduct1.getPrice());

            // DB 확인
            Cart cart = cartRepository.findByUserWithItems(testUser).orElseThrow();
            assertThat(cart.getItems()).hasSize(1);
        }

        @Test
        @DisplayName("성공: 기존 상품 수량 증가")
        void addToCart_Success_UpdateQuantity() {
            // Given - 이미 장바구니에 상품이 있음
            Long userId = testUser.getId();
            Long productId = testProduct1.getId();

            CartItem firstAdd = cartService.addToCart(userId, productId, 2);
            Long cartItemId = firstAdd.getId();

            // When - 같은 상품 추가
            CartItem result = cartService.addToCart(userId, productId, 3);

            // Then - 수량만 증가, ID는 동일
            assertThat(result.getId()).isEqualTo(cartItemId);
            assertThat(result.getQuantity()).isEqualTo(5); // 2 + 3

            // DB 확인 - 항목은 1개만 있어야 함
            Cart cart = cartRepository.findByUserWithItems(testUser).orElseThrow();
            assertThat(cart.getItems()).hasSize(1);
        }

        @Test
        @DisplayName("성공: 여러 상품 추가")
        void addToCart_Success_MultipleProducts() {
            // Given
            Long userId = testUser.getId();

            // When
            cartService.addToCart(userId, testProduct1.getId(), 2);
            cartService.addToCart(userId, testProduct2.getId(), 1);

            // Then
            Cart cart = cartRepository.findByUserWithItems(testUser).orElseThrow();
            assertThat(cart.getItems()).hasSize(2);
        }

        @Test
        @DisplayName("실패: 수량이 0 이하")
        void addToCart_Fail_InvalidQuantity() {
            // Given
            Long userId = testUser.getId();
            Long productId = testProduct1.getId();

            // When & Then
            assertThatThrownBy(() ->
                    cartService.addToCart(userId, productId, 0)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("수량은 1 이상이어야 합니다");

            assertThatThrownBy(() ->
                    cartService.addToCart(userId, productId, -1)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("수량은 1 이상이어야 합니다");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 상품")
        void addToCart_Fail_ProductNotFound() {
            // Given
            Long userId = testUser.getId();
            Long nonExistentProductId = 99999L;

            // When & Then
            assertThatThrownBy(() ->
                    cartService.addToCart(userId, nonExistentProductId, 1)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("상품을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("실패: 판매 중이 아닌 상품")
        void addToCart_Fail_ProductNotAvailable() {
            // Given - 품절 상품 생성
            Product soldOutProduct = productRepository.save(Product.builder()
                    .name("품절상품_" + System.currentTimeMillis())
                    .price(BigDecimal.valueOf(10000))
                    .stock(0)
                    .safetyStock(0)
                    .status(ProductStatus.OUT_OF_STOCK)
                    .category(testCategory)
                    .build());

            // When & Then
            assertThatThrownBy(() ->
                    cartService.addToCart(testUser.getId(), soldOutProduct.getId(), 1)
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("판매 중인 상품이 아닙니다");
        }

        @Test
        @DisplayName("실패: 재고 부족")
        void addToCart_Fail_InsufficientStock() {
            // Given
            Long userId = testUser.getId();
            Long productId = testProduct1.getId();
            int requestQuantity = testProduct1.getStock() + 10; // 재고보다 많이 요청

            // When & Then
            assertThatThrownBy(() ->
                    cartService.addToCart(userId, productId, requestQuantity)
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("재고가 부족합니다");
        }
    }

    @Nested
    @DisplayName("UC-009: 장바구니 수량 변경 통합 테스트")
    class UpdateCartItemQuantityIntegrationTest {

        @Test
        @DisplayName("성공: 수량 증가")
        void updateQuantity_Success_Increase() {
            // Given - 장바구니에 상품 추가
            CartItem cartItem = cartService.addToCart(testUser.getId(), testProduct1.getId(), 2);
            Long cartItemId = cartItem.getId();

            // When - 수량 증가
            cartService.updateCartItemQuantity(cartItemId, 5);

            // Then
            CartItem updated = cartItemRepository.findById(cartItemId).orElseThrow();
            assertThat(updated.getQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("성공: 수량 감소")
        void updateQuantity_Success_Decrease() {
            // Given
            CartItem cartItem = cartService.addToCart(testUser.getId(), testProduct1.getId(), 10);
            Long cartItemId = cartItem.getId();

            // When
            cartService.updateCartItemQuantity(cartItemId, 3);

            // Then
            CartItem updated = cartItemRepository.findById(cartItemId).orElseThrow();
            assertThat(updated.getQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("실패: 수량이 0 이하")
        void updateQuantity_Fail_InvalidQuantity() {
            // Given
            CartItem cartItem = cartService.addToCart(testUser.getId(), testProduct1.getId(), 2);

            // When & Then
            assertThatThrownBy(() ->
                    cartService.updateCartItemQuantity(cartItem.getId(), 0)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("수량은 1 이상이어야 합니다");
        }

        @Test
        @DisplayName("실패: 재고 부족")
        void updateQuantity_Fail_InsufficientStock() {
            // Given
            CartItem cartItem = cartService.addToCart(testUser.getId(), testProduct1.getId(), 2);
            int excessiveQuantity = testProduct1.getStock() + 10;

            // When & Then
            assertThatThrownBy(() ->
                    cartService.updateCartItemQuantity(cartItem.getId(), excessiveQuantity)
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("재고가 부족합니다");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 장바구니 항목")
        void updateQuantity_Fail_CartItemNotFound() {
            // Given
            Long nonExistentCartItemId = 99999L;

            // When & Then
            assertThatThrownBy(() ->
                    cartService.updateCartItemQuantity(nonExistentCartItemId, 5)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("장바구니 항목을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("UC-010: 장바구니 항목 삭제 통합 테스트")
    class RemoveCartItemIntegrationTest {

        @Test
        @DisplayName("성공: 항목 삭제")
        void removeCartItem_Success() {
            // Given - 장바구니에 상품 2개 추가
            CartItem item1 = cartService.addToCart(testUser.getId(), testProduct1.getId(), 2);
            CartItem item2 = cartService.addToCart(testUser.getId(), testProduct2.getId(), 1);

            Long cartItemIdToRemove = item1.getId();

            // When
            cartService.removeCartItem(cartItemIdToRemove);

            // Then - item1은 삭제, item2는 남아있음
            assertThat(cartItemRepository.findById(cartItemIdToRemove)).isEmpty();
            assertThat(cartItemRepository.findById(item2.getId())).isPresent();

            // 장바구니에는 1개만 남음
            Cart cart = cartRepository.findByUserWithItems(testUser).orElseThrow();
            assertThat(cart.getItems()).hasSize(1);
            assertThat(cart.getItems().get(0).getId()).isEqualTo(item2.getId());
        }

        @Test
        @DisplayName("성공: 마지막 항목 삭제")
        void removeCartItem_Success_LastItem() {
            // Given
            CartItem cartItem = cartService.addToCart(testUser.getId(), testProduct1.getId(), 2);

            // When
            cartService.removeCartItem(cartItem.getId());

            // Then - 장바구니는 남아있지만 항목은 비어있음
            Cart cart = cartRepository.findByUserWithItems(testUser).orElseThrow();
            assertThat(cart.getItems()).isEmpty();
        }

        @Test
        @DisplayName("실패: 존재하지 않는 장바구니 항목")
        void removeCartItem_Fail_NotFound() {
            // Given
            Long nonExistentCartItemId = 99999L;

            // When & Then
            assertThatThrownBy(() ->
                    cartService.removeCartItem(nonExistentCartItemId)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("장바구니 항목을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("UC-016: 장바구니 비우기 통합 테스트")
    class ClearCartIntegrationTest {

        @Test
        @DisplayName("성공: 장바구니 비우기")
        void clearCart_Success() {
            // Given - 장바구니에 상품 여러 개 추가
            cartService.addToCart(testUser.getId(), testProduct1.getId(), 2);
            cartService.addToCart(testUser.getId(), testProduct2.getId(), 3);

            Cart cart = cartRepository.findByUserWithItems(testUser).orElseThrow();
            assertThat(cart.getItems()).hasSize(2);

            // When
            cartService.clearCart(testUser.getId());

            // Then - 장바구니는 남아있지만 항목은 모두 삭제
            Cart clearedCart = cartRepository.findByUserWithItems(testUser).orElseThrow();
            assertThat(clearedCart.getItems()).isEmpty();

            // DB 확인
            List<CartItem> remainingItems = cartItemRepository.findAll();
            assertThat(remainingItems).isEmpty();
        }

        @Test
        @DisplayName("성공: 이미 비어있는 장바구니 비우기")
        void clearCart_Success_AlreadyEmpty() {
            // Given - 빈 장바구니 조회 (자동 생성)
            Cart emptyCart = cartService.getCart(testUser.getId());
            assertThat(emptyCart.getItems()).isEmpty();

            // When - 예외 발생하지 않아야 함
            assertThatNoException().isThrownBy(() ->
                    cartService.clearCart(testUser.getId())
            );

            // Then
            Cart cart = cartRepository.findByUserWithItems(testUser).orElseThrow();
            assertThat(cart.getItems()).isEmpty();
        }

        @Test
        @DisplayName("실패: 존재하지 않는 사용자")
        void clearCart_Fail_UserNotFound() {
            // Given
            Long nonExistentUserId = 99999L;

            // When & Then
            assertThatThrownBy(() ->
                    cartService.clearCart(nonExistentUserId)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("사용자를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("실패: 장바구니가 없는 경우")
        void clearCart_Fail_CartNotFound() {
            // Given - 장바구니를 한 번도 조회하지 않은 사용자
            String uniqueEmail = "newuser_" + System.currentTimeMillis() + "@example.com";
            User newUser = userRepository.save(User.builder()
                    .email(uniqueEmail)
                    .password("password123")
                    .name("신규사용자")
                    .balance(BigDecimal.valueOf(100000))
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .build());

            // When & Then
            assertThatThrownBy(() ->
                    cartService.clearCart(newUser.getId())
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("장바구니를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("JPA 영속성 및 관계 테스트")
    class JPAPersistenceTest {

        @Test
        @DisplayName("성공: clearCart - 장바구니 비우기로 모든 항목 삭제")
        void clearCart_RemovesAllItems() {
            // Given - 장바구니에 상품 추가
            cartService.addToCart(testUser.getId(), testProduct1.getId(), 2);
            cartService.addToCart(testUser.getId(), testProduct2.getId(), 1);

            long itemCountBefore = cartItemRepository.count();
            assertThat(itemCountBefore).isEqualTo(2);

            // When - 장바구니 비우기
            cartService.clearCart(testUser.getId());

            // Then - 모든 항목이 삭제됨
            long itemCountAfter = cartItemRepository.count();
            assertThat(itemCountAfter).isEqualTo(0);

            Cart cart = cartRepository.findByUserWithItems(testUser).orElseThrow();
            assertThat(cart.getItems()).isEmpty();
        }

        @Test
        @DisplayName("성공: Unique 제약조건 - 한 장바구니에 같은 상품 중복 불가")
        void unique_Constraint_CartAndProduct() {
            // Given - 장바구니에 상품 추가
            CartItem item1 = cartService.addToCart(testUser.getId(), testProduct1.getId(), 2);

            // When - 같은 상품 추가 시도 (서비스는 수량만 증가)
            CartItem item2 = cartService.addToCart(testUser.getId(), testProduct1.getId(), 3);

            // Then - 같은 CartItem (ID 동일, 수량만 증가)
            assertThat(item1.getId()).isEqualTo(item2.getId());
            assertThat(item2.getQuantity()).isEqualTo(5);

            // DB에는 1개만 존재
            Cart cart = cartRepository.findByUserWithItems(testUser).orElseThrow();
            assertThat(cart.getItems()).hasSize(1);
        }

        @Test
        @DisplayName("성공: getCart - 장바구니 조회 시 항목 포함")
        void getCart_IncludesItems() {
            // Given
            cartService.addToCart(testUser.getId(), testProduct1.getId(), 2);
            cartService.addToCart(testUser.getId(), testProduct2.getId(), 1);

            // When - Service를 통한 조회 (N+1 방지된 조회)
            Cart cart = cartService.getCart(testUser.getId());

            // Then - 항목이 포함되어 조회됨
            assertThat(cart.getItems()).isNotEmpty();
            assertThat(cart.getItems()).hasSize(2);
            assertThat(cart.getItems()).extracting("quantity")
                    .containsExactlyInAnyOrder(2, 1);
        }
    }
}

package com.hhplus.ecommerce.application.cart;

import com.hhplus.ecommerce.common.FakeRepositorySupport;
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
import com.hhplus.ecommerce.infrastructure.persistence.product.ProductRepository;
import com.hhplus.ecommerce.infrastructure.persistence.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * CartService 단위 테스트
 *
 * 테스트 전략:
 * - 인메모리 데이터(Map, List) 사용
 * - Given-When-Then 패턴
 */
@DisplayName("CartService 단위 테스트")
class CartServiceTest {

    private CartRepository cartRepository;
    private CartItemRepository cartItemRepository;
    private ProductRepository productRepository;
    private UserRepository userRepository;
    private CartService cartService;

    private User testUser;
    private Cart testCart;
    private Product testProduct1;
    private Product testProduct2;
    private Category testCategory;

    /**
     * Fake UserRepository - 인메모리 Map 사용
     */
    static class FakeUserRepository extends FakeRepositorySupport<User, Long> implements UserRepository {
        private final Map<Long, User> store = new HashMap<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Override
        public User save(User user) {
            if (user.getId() == null) {
                Long newId = idGenerator.getAndIncrement();
                User newUser = User.builder()
                        .id(newId)
                        .email(user.getEmail())
                        .password(user.getPassword())
                        .name(user.getName())
                        .balance(user.getBalance())
                        .role(user.getRole())
                        .status(user.getStatus())
                        .build();
                store.put(newId, newUser);
                return newUser;
            }
            store.put(user.getId(), user);
            return user;
        }

        @Override
        public Optional<User> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        @Override
        public void delete(User user) {
            store.remove(user.getId());
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
        public List<User> findAllById(Iterable<Long> ids) {
            return new ArrayList<>();
        }

        @Override
        public boolean existsByEmail(String email) {
            return store.values().stream()
                    .anyMatch(user -> user.getEmail().equals(email));
        }

        @Override
        public Optional<User> findByIdWithLock(Long id) {
            return findById(id);
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return store.values().stream()
                    .filter(user -> user.getEmail().equals(email))
                    .findFirst();
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
        public Page<Product> findAvailableProducts(Pageable pageable) {
            return Page.empty();
        }

        @Override
        public Page<Product> findByCategoryId(Long categoryId, Pageable pageable) {
            return Page.empty();
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
     * Fake CartRepository - 인메모리 Map 사용
     */
    static class FakeCartRepository extends FakeRepositorySupport<Cart, Long> implements CartRepository {
        private final Map<Long, Cart> store = new HashMap<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Override
        public Cart save(Cart cart) {
            if (cart.getId() == null) {
                Long newId = idGenerator.getAndIncrement();
                Cart newCart = Cart.builder()
                        .id(newId)
                        .user(cart.getUser())
                        .build();
                store.put(newId, newCart);
                return newCart;
            }
            store.put(cart.getId(), cart);
            return cart;
        }

        @Override
        public Optional<Cart> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Cart> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        @Override
        public void delete(Cart cart) {
            store.remove(cart.getId());
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
        public List<Cart> findAllById(Iterable<Long> ids) {
            return new ArrayList<>();
        }

        @Override
        public Optional<Cart> findByUser(User user) {
            return store.values().stream()
                    .filter(cart -> cart.getUser().getId().equals(user.getId()))
                    .findFirst();
        }

        @Override
        public Optional<Cart> findByUserWithItems(User user) {
            return findByUser(user);
        }

        @Override
        public boolean existsByUserId(Long userId) {
            return store.values().stream()
                    .anyMatch(cart -> cart.getUser().getId().equals(userId));
        }

        public void clear() {
            store.clear();
            idGenerator.set(1);
        }
    }

    /**
     * Fake CartItemRepository - 인메모리 List 사용
     */
    static class FakeCartItemRepository extends FakeRepositorySupport<CartItem, Long> implements CartItemRepository {
        private final List<CartItem> store = new ArrayList<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Override
        public CartItem save(CartItem cartItem) {
            if (cartItem.getId() == null) {
                CartItem newCartItem = CartItem.builder()
                        .id(idGenerator.getAndIncrement())
                        .cart(cartItem.getCart())
                        .product(cartItem.getProduct())
                        .quantity(cartItem.getQuantity())
                        .priceAtAdd(cartItem.getPriceAtAdd())
                        .build();
                store.add(newCartItem);
                return newCartItem;
            }
            // 기존 항목 업데이트
            store.removeIf(item -> item.getId().equals(cartItem.getId()));
            store.add(cartItem);
            return cartItem;
        }

        @Override
        public Optional<CartItem> findById(Long id) {
            return store.stream()
                    .filter(item -> item.getId().equals(id))
                    .findFirst();
        }

        @Override
        public List<CartItem> findAll() {
            return new ArrayList<>(store);
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        @Override
        public void delete(CartItem cartItem) {
            store.removeIf(item -> item.getId().equals(cartItem.getId()));
        }

        @Override
        public void deleteById(Long id) {
            store.removeIf(item -> item.getId().equals(id));
        }

        @Override
        public boolean existsById(Long id) {
            return store.stream().anyMatch(item -> item.getId().equals(id));
        }

        @Override
        public List<CartItem> findAllById(Iterable<Long> ids) {
            return new ArrayList<>();
        }

        @Override
        public Optional<CartItem> findByCartAndProduct(Cart cart, Product product) {
            return store.stream()
                    .filter(item -> item.getCart().getId().equals(cart.getId()) &&
                                    item.getProduct().getId().equals(product.getId()))
                    .findFirst();
        }

        @Override
        public List<CartItem> findByCart(Cart cart) {
            return store.stream()
                    .filter(item -> item.getCart().getId().equals(cart.getId()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<CartItem> findByCartWithProduct(Cart cart) {
            return findByCart(cart);
        }

        @Override
        public Long countByCart(Cart cart) {
            return (long) store.stream()
                    .filter(item -> item.getCart().getId().equals(cart.getId()))
                    .count();
        }

        @Override
        public void deleteByCart(Cart cart) {
            store.removeIf(item -> item.getCart().getId().equals(cart.getId()));
        }

        public void clear() {
            store.clear();
            idGenerator.set(1);
        }
    }

    @BeforeEach
    void setUp() {
        FakeUserRepository fakeUserRepo = new FakeUserRepository();
        FakeProductRepository fakeProductRepo = new FakeProductRepository();
        FakeCartRepository fakeCartRepo = new FakeCartRepository();
        FakeCartItemRepository fakeCartItemRepo = new FakeCartItemRepository();

        fakeUserRepo.clear();
        fakeProductRepo.clear();
        fakeCartRepo.clear();
        fakeCartItemRepo.clear();

        userRepository = fakeUserRepo;
        productRepository = fakeProductRepo;
        cartRepository = fakeCartRepo;
        cartItemRepository = fakeCartItemRepo;
        cartService = new CartService(cartRepository, cartItemRepository, productRepository, userRepository);

        // 인메모리 테스트 데이터 생성
        testCategory = createCategory(1L, "전자제품");
        testUser = createUser(1L, "test@test.com");
        testProduct1 = createProduct(1L, "노트북", 50, ProductStatus.AVAILABLE, BigDecimal.valueOf(1000000));
        testProduct2 = createProduct(2L, "마우스", 100, ProductStatus.AVAILABLE, BigDecimal.valueOf(30000));
        testCart = createCart(1L, testUser);

        userRepository.save(testUser);
        productRepository.save(testProduct1);
        productRepository.save(testProduct2);
        cartRepository.save(testCart);
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
            User newUser = createUser(null, "new@test.com");
            newUser = userRepository.save(newUser);
            Long userId = newUser.getId();

            // When
            Cart result = cartService.getCart(userId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUser().getId()).isEqualTo(userId);

            // 인메모리에서 확인
            Optional<Cart> savedCart = cartRepository.findByUser(newUser);
            assertThat(savedCart).isPresent();
        }

        @Test
        @DisplayName("실패: 사용자를 찾을 수 없음")
        void getCart_UserNotFound() {
            // Given
            Long userId = 999L;

            // When & Then
            assertThatThrownBy(() -> cartService.getCart(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
        }
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

            // 인메모리에서 확인
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
            CartItem existingItem = createCartItem(null, testCart, testProduct1, initialQuantity);
            cartItemRepository.save(existingItem);

            // When
            CartItem result = cartService.addToCart(userId, productId, addQuantity);

            // Then
            assertThat(result.getQuantity()).isEqualTo(initialQuantity + addQuantity); // 2 + 3 = 5
            assertThat(result.getUpdatedAt()).isNotNull();

            // 인메모리에서 확인 - 항목이 중복되지 않고 수량만 증가해야 함
            List<CartItem> items = cartItemRepository.findByCart(testCart);
            assertThat(items).hasSize(1);
        }

        @Test
        @DisplayName("성공: 장바구니 없을 때 자동 생성 후 추가")
        void addToCart_CreateCartIfNotExists() {
            // Given
            User newUser = createUser(null, "new@test.com");
            newUser = userRepository.save(newUser);
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
        @DisplayName("실패: 수량이 0 이하")
        void addToCart_InvalidQuantity() {
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
        @DisplayName("실패: 사용자를 찾을 수 없음")
        void addToCart_UserNotFound() {
            // Given
            Long userId = 999L;
            Long productId = testProduct1.getId();
            Integer quantity = 1;

            // When & Then
            assertThatThrownBy(() -> cartService.addToCart(userId, productId, quantity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("실패: 상품을 찾을 수 없음")
        void addToCart_ProductNotFound() {
            // Given
            Long userId = testUser.getId();
            Long productId = 999L;
            Integer quantity = 1;

            // When & Then
            assertThatThrownBy(() -> cartService.addToCart(userId, productId, quantity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상품을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("실패: 판매 중인 상품이 아님")
        void addToCart_ProductNotAvailable() {
            // Given
            Long userId = testUser.getId();
            Product unavailableProduct = createProduct(null, "품절상품", 0, ProductStatus.OUT_OF_STOCK, BigDecimal.valueOf(10000));
            unavailableProduct = productRepository.save(unavailableProduct);
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

    @Nested
    @DisplayName("장바구니 수량 변경 테스트")
    class UpdateCartItemQuantityTest {

        @Test
        @DisplayName("성공: 수량 변경")
        void updateCartItemQuantity_Success() {
            // Given
            Integer initialQuantity = 2;
            Integer newQuantity = 5;
            CartItem cartItem = createCartItem(null, testCart, testProduct1, initialQuantity);
            cartItem = cartItemRepository.save(cartItem);
            Long cartItemId = cartItem.getId();

            // When
            cartService.updateCartItemQuantity(cartItemId, newQuantity);

            // Then
            CartItem updated = cartItemRepository.findById(cartItemId).orElseThrow();
            assertThat(updated.getQuantity()).isEqualTo(newQuantity);
            assertThat(updated.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("실패: 수량이 0 이하")
        void updateCartItemQuantity_InvalidQuantity() {
            // Given
            Long cartItemId = 1L;
            Integer newQuantity = 0;

            // When & Then
            assertThatThrownBy(() -> cartService.updateCartItemQuantity(cartItemId, newQuantity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수량은 1 이상이어야 합니다");
        }

        @Test
        @DisplayName("실패: 장바구니 항목을 찾을 수 없음")
        void updateCartItemQuantity_CartItemNotFound() {
            // Given
            Long cartItemId = 999L;
            Integer newQuantity = 3;

            // When & Then
            assertThatThrownBy(() -> cartService.updateCartItemQuantity(cartItemId, newQuantity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("장바구니 항목을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("실패: 재고 부족")
        void updateCartItemQuantity_InsufficientStock() {
            // Given
            CartItem cartItem = createCartItem(null, testCart, testProduct1, 2);
            cartItem = cartItemRepository.save(cartItem);
            Long cartItemId = cartItem.getId();
            Integer newQuantity = 100; // 재고(50)보다 많음

            // When & Then
            assertThatThrownBy(() -> cartService.updateCartItemQuantity(cartItemId, newQuantity))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재고가 부족합니다");
        }
    }

    @Nested
    @DisplayName("장바구니 항목 삭제 테스트")
    class RemoveCartItemTest {

        @Test
        @DisplayName("성공: 장바구니 항목 삭제")
        void removeCartItem_Success() {
            // Given
            CartItem cartItem = createCartItem(null, testCart, testProduct1, 2);
            cartItem = cartItemRepository.save(cartItem);
            Long cartItemId = cartItem.getId();

            // When
            cartService.removeCartItem(cartItemId);

            // Then
            Optional<CartItem> deleted = cartItemRepository.findById(cartItemId);
            assertThat(deleted).isEmpty();
        }

        @Test
        @DisplayName("실패: 장바구니 항목을 찾을 수 없음")
        void removeCartItem_CartItemNotFound() {
            // Given
            Long cartItemId = 999L;

            // When & Then
            assertThatThrownBy(() -> cartService.removeCartItem(cartItemId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("장바구니 항목을 찾을 수 없습니다");
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
            .balance(BigDecimal.valueOf(100000))
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .build();
    }

    private Category createCategory(Long id, String name) {
        return Category.builder()
            .id(id)
            .name(name)
            .description("테스트 카테고리")
            .build();
    }

    private Product createProduct(Long id, String name, int stock, ProductStatus status, BigDecimal price) {
        return Product.builder()
            .id(id)
            .name(name)
            .description(name + " 설명")
            .price(price)
            .stock(stock)
            .safetyStock(10)
            .category(testCategory)
            .status(status)
            .version(0L)
            .build();
    }

    private Cart createCart(Long id, User user) {
        return Cart.builder()
            .id(id)
            .user(user)
            .build();
    }

    private CartItem createCartItem(Long id, Cart cart, Product product, Integer quantity) {
        return CartItem.builder()
            .id(id)
            .cart(cart)
            .product(product)
            .quantity(quantity)
            .priceAtAdd(product.getPrice())
            .build();
    }
}

package com.hhplus.ecommerce.application.order;

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
import com.hhplus.ecommerce.domain.product.StockHistory;
import com.hhplus.ecommerce.domain.user.BalanceHistory;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRole;
import com.hhplus.ecommerce.domain.user.UserStatus;
import com.hhplus.ecommerce.infrastructure.persistence.cart.CartRepository;
import com.hhplus.ecommerce.infrastructure.persistence.coupon.UserCouponRepository;
import com.hhplus.ecommerce.infrastructure.persistence.order.OrderRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.ProductRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.StockHistoryRepository;
import com.hhplus.ecommerce.infrastructure.persistence.user.BalanceHistoryRepository;
import com.hhplus.ecommerce.infrastructure.persistence.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.FluentQuery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * OrderService 단위 테스트
 *
 * 테스트 전략:
 * - 인메모리 데이터(Map, List) 사용
 * - Given-When-Then 패턴
 * - 주문 생성의 복잡한 플로우 검증
 */
@DisplayName("OrderService 단위 테스트")
class OrderServiceTest {

    private OrderRepository orderRepository;
    private UserRepository userRepository;
    private ProductRepository productRepository;
    private CartRepository cartRepository;
    private UserCouponRepository userCouponRepository;
    private BalanceHistoryRepository balanceHistoryRepository;
    private StockHistoryRepository stockHistoryRepository;
    private OrderService orderService;

    private User testUser;
    private Category testCategory;
    private Product testProduct1;
    private Product testProduct2;
    private Cart testCart;
    private CartItem cartItem1;
    private CartItem cartItem2;
    private Coupon testCoupon;
    private UserCoupon testUserCoupon;

    /**
     * Fake OrderRepository - 인메모리 Map 사용
     */
    static abstract class FakeOrderRepository implements OrderRepository {
        private final Map<Long, Order> store = new HashMap<>();
        private final Map<String, Order> idempotencyStore = new HashMap<>();
        private final Map<String, Order> orderNumberStore = new HashMap<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Override
        public Order save(Order order) {
            if (order.getId() == null) {
                Long newId = idGenerator.getAndIncrement();
                Order newOrder = Order.builder()
                        .id(newId)
                        .orderNumber(order.getOrderNumber())
                        .user(order.getUser())
                        .totalAmount(order.getTotalAmount())
                        .discountAmount(order.getDiscountAmount())
                        .finalAmount(order.getFinalAmount())
                        .status(order.getStatus())
                        .orderedAt(order.getOrderedAt())
                        .paidAt(order.getPaidAt())
                        .idempotencyKey(order.getIdempotencyKey())
                        .build();
                store.put(newId, newOrder);
                idempotencyStore.put(newOrder.getIdempotencyKey(), newOrder);
                orderNumberStore.put(newOrder.getOrderNumber(), newOrder);
                return newOrder;
            }
            store.put(order.getId(), order);
            idempotencyStore.put(order.getIdempotencyKey(), order);
            orderNumberStore.put(order.getOrderNumber(), order);
            return order;
        }

        @Override
        public Optional<Order> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Order> findByIdWithDetails(Long id) {
            return findById(id);
        }

        @Override
        public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
            return Optional.ofNullable(idempotencyStore.get(idempotencyKey));
        }

        @Override
        public Optional<Order> findByOrderNumber(String orderNumber) {
            return Optional.ofNullable(orderNumberStore.get(orderNumber));
        }

        @Override
        public Page<Order> findByUserOrderByOrderedAtDesc(User user, Pageable pageable) {
            List<Order> filtered = store.values().stream()
                    .filter(order -> order.getUser().getId().equals(user.getId()))
                    .sorted(Comparator.comparing(Order::getOrderedAt).reversed())
                    .skip(pageable.getOffset())
                    .limit(pageable.getPageSize())
                    .collect(Collectors.toList());

            long total = store.values().stream()
                    .filter(order -> order.getUser().getId().equals(user.getId()))
                    .count();

            return new PageImpl<>(filtered, pageable, total);
        }

        @Override
        public Long countTodayOrders(LocalDate date) {
            return store.values().stream()
                    .filter(order -> order.getOrderedAt().toLocalDate().equals(date))
                    .count();
        }

        @Override
        public Page<Order> findByUserAndStatus(User user, OrderStatus status, Pageable pageable) {
            List<Order> filtered = store.values().stream()
                    .filter(order -> order.getUser().getId().equals(user.getId()))
                    .filter(order -> order.getStatus() == status)
                    .sorted(Comparator.comparing(Order::getOrderedAt).reversed())
                    .skip(pageable.getOffset())
                    .limit(pageable.getPageSize())
                    .collect(Collectors.toList());

            long total = store.values().stream()
                    .filter(order -> order.getUser().getId().equals(user.getId()))
                    .filter(order -> order.getStatus() == status)
                    .count();

            return new PageImpl<>(filtered, pageable, total);
        }

        @Override
        public List<Order> findByOrderedAtBetween(LocalDate startDate, LocalDate endDate) {
            return store.values().stream()
                    .filter(order -> {
                        LocalDate orderDate = order.getOrderedAt().toLocalDate();
                        return !orderDate.isBefore(startDate) && !orderDate.isAfter(endDate);
                    })
                    .collect(Collectors.toList());
        }

        @Override
        public List<Order> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void deleteAll() {
            store.clear();
            idempotencyStore.clear();
            orderNumberStore.clear();
        }

        // JpaRepository stub methods
        @Override public <S extends Order> List<S> findAll(org.springframework.data.domain.Example<S> example) { return new ArrayList<>(); }
        @Override public <S extends Order> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return new ArrayList<>(); }
        @Override public Order getReferenceById(Long id) { return findById(id).orElse(null); }
        @Override public void flush() {}
        @Override @SuppressWarnings("unchecked") public <S extends Order> S saveAndFlush(S entity) { return (S) save(entity); }
        @Override @SuppressWarnings("unchecked") public <S extends Order> List<S> saveAllAndFlush(Iterable<S> entities) { List<S> r = new ArrayList<>(); entities.forEach(e -> r.add((S) save(e))); return r; }
        @Override public void deleteAllInBatch(Iterable<Order> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public Order getOne(Long id) { return findById(id).orElse(null); }
        @Override public Order getById(Long id) { return findById(id).orElseThrow(); }
        @Override @SuppressWarnings("unchecked") public <S extends Order> List<S> saveAll(Iterable<S> entities) { List<S> r = new ArrayList<>(); entities.forEach(e -> r.add((S) save(e))); return r; }
        @Override public void deleteById(Long id) {}
        @Override public void delete(Order entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> ids) {}
        @Override public void deleteAll(Iterable<? extends Order> entities) {}
        @Override public List<Order> findAllById(Iterable<Long> ids) { return new ArrayList<>(); }
        @Override public List<Order> findAll(org.springframework.data.domain.Sort sort) { return findAll(); }
        @Override public org.springframework.data.domain.Page<Order> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends Order> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends Order> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends Order> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends Order> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends Order, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public boolean existsById(Long id) { return false; }
        @Override public long count() { return 0; }

        public void clear() {
            store.clear();
            idempotencyStore.clear();
            orderNumberStore.clear();
            idGenerator.set(1);
        }
    }

    /**
     * Fake UserRepository - 인메모리 Map 사용
     */
    static abstract class FakeUserRepository implements UserRepository {
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
        public boolean existsByEmail(String email) {
            return store.values().stream()
                    .anyMatch(user -> user.getEmail().equals(email));
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return store.values().stream()
                    .filter(user -> user.getEmail().equals(email))
                    .findFirst();
        }

        @Override
        public Optional<User> findByIdWithLock(Long id) {
            return findById(id);
        }

        @Override
        public void delete(User user) {
            store.remove(user.getId());
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        // JpaRepository stub methods
        @Override public <S extends User> List<S> findAll(org.springframework.data.domain.Example<S> example) { return new ArrayList<>(); }
        @Override public <S extends User> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return new ArrayList<>(); }
        @Override public User getReferenceById(Long id) { return findById(id).orElse(null); }
        @Override public void flush() {}
        @Override @SuppressWarnings("unchecked") public <S extends User> S saveAndFlush(S entity) { return (S) save(entity); }
        @Override @SuppressWarnings("unchecked") public <S extends User> List<S> saveAllAndFlush(Iterable<S> entities) { List<S> r = new ArrayList<>(); entities.forEach(e -> r.add((S) save(e))); return r; }
        @Override public void deleteAllInBatch(Iterable<User> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public User getOne(Long id) { return findById(id).orElse(null); }
        @Override public User getById(Long id) { return findById(id).orElseThrow(); }
        @Override @SuppressWarnings("unchecked") public <S extends User> List<S> saveAll(Iterable<S> entities) { List<S> r = new ArrayList<>(); entities.forEach(e -> r.add((S) save(e))); return r; }
        @Override public void deleteById(Long id) {}
        @Override public void deleteAllById(Iterable<? extends Long> ids) {}
        @Override public void deleteAll(Iterable<? extends User> entities) {}
        @Override public List<User> findAllById(Iterable<Long> ids) { return new ArrayList<>(); }
        @Override public List<User> findAll(org.springframework.data.domain.Sort sort) { return findAll(); }
        @Override public org.springframework.data.domain.Page<User> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends User> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends User> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends User> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends User> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends User, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public boolean existsById(Long id) { return false; }
        @Override public long count() { return 0; }

        public void clear() {
            store.clear();
            idGenerator.set(1);
        }
    }

    /**
     * Fake ProductRepository - 인메모리 Map 사용
     */
    static abstract class FakeProductRepository implements ProductRepository {
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
                        .version(product.getVersion())
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
        public Page<Product> findAvailableProducts(Pageable pageable) {
            return Page.empty();
        }

        @Override
        public Page<Product> findByCategoryId(Long categoryId, Pageable pageable) {
            return Page.empty();
        }

        @Override
        public List<Product> findAllById(Iterable<Long> ids) {
            return new ArrayList<>();
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
            return store.values().stream()
                    .filter(product -> product.getStatus() == status)
                    .collect(Collectors.toList());
        }

        @Override
        public void delete(Product product) {
            store.remove(product.getId());
        }

        @Override
        public List<Product> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        // JpaRepository stub methods
        @Override public <S extends Product> List<S> findAll(org.springframework.data.domain.Example<S> example) { return new ArrayList<>(); }
        @Override public <S extends Product> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return new ArrayList<>(); }
        @Override public Product getReferenceById(Long id) { return findById(id).orElse(null); }
        @Override public void flush() {}
        @Override @SuppressWarnings("unchecked") public <S extends Product> S saveAndFlush(S entity) { return (S) save(entity); }
        @Override @SuppressWarnings("unchecked") public <S extends Product> List<S> saveAllAndFlush(Iterable<S> entities) { List<S> r = new ArrayList<>(); entities.forEach(e -> r.add((S) save(e))); return r; }
        @Override public void deleteAllInBatch(Iterable<Product> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public Product getOne(Long id) { return findById(id).orElse(null); }
        @Override public Product getById(Long id) { return findById(id).orElseThrow(); }
        @Override @SuppressWarnings("unchecked") public <S extends Product> List<S> saveAll(Iterable<S> entities) { List<S> r = new ArrayList<>(); entities.forEach(e -> r.add((S) save(e))); return r; }
        @Override public void deleteById(Long id) {}
        @Override public void deleteAllById(Iterable<? extends Long> ids) {}
        @Override public void deleteAll(Iterable<? extends Product> entities) {}
        @Override public List<Product> findAll(org.springframework.data.domain.Sort sort) { return findAll(); }
        @Override public org.springframework.data.domain.Page<Product> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends Product> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends Product> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends Product> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends Product> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends Product, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public boolean existsById(Long id) { return false; }
        @Override public long count() { return 0; }

        public void clear() {
            store.clear();
            idGenerator.set(1);
        }
    }

    /**
     * Fake CartRepository - 인메모리 Map 사용
     */
    static abstract class FakeCartRepository implements CartRepository {
        private final Map<Long, Cart> store = new HashMap<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Override
        public Cart save(Cart cart) {
            if (cart.getId() == null) {
                Long newId = idGenerator.getAndIncrement();
                Cart newCart = Cart.builder()
                        .id(newId)
                        .user(cart.getUser())
                        .items(cart.getItems())
                        .build();
                store.put(newId, newCart);
                return newCart;
            }
            store.put(cart.getId(), cart);
            return cart;
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
        public Optional<Cart> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public boolean existsByUserId(Long userId) {
            return store.values().stream()
                    .anyMatch(cart -> cart.getUser().getId().equals(userId));
        }

        @Override
        public void delete(Cart cart) {
            store.remove(cart.getId());
        }

        @Override
        public List<Cart> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        // JpaRepository stub methods
        @Override public <S extends Cart> List<S> findAll(org.springframework.data.domain.Example<S> example) { return new ArrayList<>(); }
        @Override public <S extends Cart> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return new ArrayList<>(); }
        @Override public Cart getReferenceById(Long id) { return findById(id).orElse(null); }
        @Override public void flush() {}
        @Override @SuppressWarnings("unchecked") public <S extends Cart> S saveAndFlush(S entity) { return (S) save(entity); }
        @Override @SuppressWarnings("unchecked") public <S extends Cart> List<S> saveAllAndFlush(Iterable<S> entities) { List<S> r = new ArrayList<>(); entities.forEach(e -> r.add((S) save(e))); return r; }
        @Override public void deleteAllInBatch(Iterable<Cart> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public Cart getOne(Long id) { return findById(id).orElse(null); }
        @Override public Cart getById(Long id) { return findById(id).orElseThrow(); }
        @Override @SuppressWarnings("unchecked") public <S extends Cart> List<S> saveAll(Iterable<S> entities) { List<S> r = new ArrayList<>(); entities.forEach(e -> r.add((S) save(e))); return r; }
        @Override public void deleteById(Long id) {}
        @Override public void deleteAllById(Iterable<? extends Long> ids) {}
        @Override public void deleteAll(Iterable<? extends Cart> entities) {}
        @Override public List<Cart> findAllById(Iterable<Long> ids) { return new ArrayList<>(); }
        @Override public List<Cart> findAll(org.springframework.data.domain.Sort sort) { return findAll(); }
        @Override public org.springframework.data.domain.Page<Cart> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends Cart> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends Cart> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends Cart> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends Cart> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends Cart, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public boolean existsById(Long id) { return false; }
        @Override public long count() { return 0; }

        public void clear() {
            store.clear();
            idGenerator.set(1);
        }
    }

    /**
     * Fake UserCouponRepository - 인메모리 List 사용
     */
    static class FakeUserCouponRepository implements UserCouponRepository {
        private final List<UserCoupon> store = new ArrayList<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Override
        public UserCoupon save(UserCoupon userCoupon) {
            if (userCoupon.getId() == null) {
                UserCoupon newUserCoupon = UserCoupon.builder()
                        .id(idGenerator.getAndIncrement())
                        .user(userCoupon.getUser())
                        .coupon(userCoupon.getCoupon())
                        .status(userCoupon.getStatus())
                        .issuedAt(userCoupon.getIssuedAt())
                        .usedAt(userCoupon.getUsedAt())
                        .build();
                store.add(newUserCoupon);
                return newUserCoupon;
            }
            store.removeIf(uc -> uc.getId().equals(userCoupon.getId()));
            store.add(userCoupon);
            return userCoupon;
        }

        @Override
        public Optional<UserCoupon> findById(Long id) {
            return store.stream()
                    .filter(uc -> uc.getId().equals(id))
                    .findFirst();
        }

        public List<UserCoupon> findByUser(User user) {
            return store.stream()
                    .filter(uc -> uc.getUser().getId().equals(user.getId()))
                    .collect(Collectors.toList());
        }

        public Optional<UserCoupon> findByUserAndCoupon(User user, Coupon coupon) {
            return store.stream()
                    .filter(uc -> uc.getUser().getId().equals(user.getId()) &&
                                  uc.getCoupon().getId().equals(coupon.getId()))
                    .findFirst();
        }

        @Override
        public Long countByUserAndCoupon(User user, Coupon coupon) {
            return store.stream()
                    .filter(uc -> uc.getUser().getId().equals(user.getId()) &&
                                  uc.getCoupon().getId().equals(coupon.getId()))
                    .count();
        }

        @Override
        public List<UserCoupon> findExpiredCoupons(LocalDateTime now) {
            return store.stream()
                    .filter(uc -> uc.getStatus() == UserCouponStatus.ISSUED)
                    .filter(uc -> uc.getCoupon().getValidUntil().isBefore(now))
                    .collect(Collectors.toList());
        }

        @Override
        public List<UserCoupon> findByUserAndStatus(User user, UserCouponStatus status) {
            return store.stream()
                    .filter(uc -> uc.getUser().getId().equals(user.getId()))
                    .filter(uc -> uc.getStatus() == status)
                    .collect(Collectors.toList());
        }

        @Override
        public List<UserCoupon> findByUserOrderByIssuedAtDesc(User user) {
            return store.stream()
                    .filter(uc -> uc.getUser().getId().equals(user.getId()))
                    .sorted(Comparator.comparing(UserCoupon::getIssuedAt).reversed())
                    .collect(Collectors.toList());
        }

        @Override
        public List<UserCoupon> findAvailableCouponsByUser(User user, LocalDateTime now) {
            return store.stream()
                    .filter(uc -> uc.getUser().getId().equals(user.getId()))
                    .filter(UserCoupon::canUse)
                    .collect(Collectors.toList());
        }

        @Override
        public List<UserCoupon> findAll() {
            return new ArrayList<>(store);
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        // JpaRepository stub methods
        @Override public <S extends UserCoupon> List<S> findAll(org.springframework.data.domain.Example<S> example) { return new ArrayList<>(); }
        @Override public <S extends UserCoupon> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return new ArrayList<>(); }
        @Override public UserCoupon getReferenceById(Long id) { return findById(id).orElse(null); }
        @Override public void flush() {}
        @Override @SuppressWarnings("unchecked") public <S extends UserCoupon> S saveAndFlush(S entity) { return (S) save(entity); }
        @Override @SuppressWarnings("unchecked") public <S extends UserCoupon> List<S> saveAllAndFlush(Iterable<S> entities) { List<S> r = new ArrayList<>(); entities.forEach(e -> r.add((S) save(e))); return r; }
        @Override public void deleteAllInBatch(Iterable<UserCoupon> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public UserCoupon getOne(Long id) { return findById(id).orElse(null); }
        @Override public UserCoupon getById(Long id) { return findById(id).orElseThrow(); }
        @Override @SuppressWarnings("unchecked") public <S extends UserCoupon> List<S> saveAll(Iterable<S> entities) { List<S> r = new ArrayList<>(); entities.forEach(e -> r.add((S) save(e))); return r; }
        @Override public void deleteById(Long id) {}
        @Override public void delete(UserCoupon entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> ids) {}
        @Override public void deleteAll(Iterable<? extends UserCoupon> entities) {}
        @Override public List<UserCoupon> findAllById(Iterable<Long> ids) { return new ArrayList<>(); }
        @Override public List<UserCoupon> findAll(org.springframework.data.domain.Sort sort) { return findAll(); }
        @Override public org.springframework.data.domain.Page<UserCoupon> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends UserCoupon> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends UserCoupon> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends UserCoupon> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends UserCoupon> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends UserCoupon, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public boolean existsById(Long id) { return false; }
        @Override public long count() { return 0; }

        public void clear() {
            store.clear();
            idGenerator.set(1);
        }
    }

    /**
     * Fake BalanceHistoryRepository - 인메모리 List 사용
     */
    static class FakeBalanceHistoryRepository implements BalanceHistoryRepository {
        private final List<BalanceHistory> store = new ArrayList<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Override
        public BalanceHistory save(BalanceHistory history) {
            if (history.getId() == null) {
                BalanceHistory newHistory = BalanceHistory.builder()
                        .id(idGenerator.getAndIncrement())
                        .user(history.getUser())
                        .type(history.getType())
                        .amount(history.getAmount())
                        .balanceBefore(history.getBalanceBefore())
                        .balanceAfter(history.getBalanceAfter())
                        .description(history.getDescription())
                        .createdAt(history.getCreatedAt())
                        .build();
                store.add(newHistory);
                return newHistory;
            }
            store.add(history);
            return history;
        }

        @Override
        public Page<BalanceHistory> findByUserOrderByCreatedAtDesc(User user, Pageable pageable) {
            return Page.empty();
        }

        @Override
        public List<BalanceHistory> findByUserAndCreatedAtBetween(User user, LocalDateTime startDate, LocalDateTime endDate) {
            return store.stream()
                    .filter(history -> history.getUser().getId().equals(user.getId()))
                    .filter(history -> !history.getCreatedAt().isBefore(startDate) && !history.getCreatedAt().isAfter(endDate))
                    .collect(Collectors.toList());
        }

        @Override
        public Optional<BalanceHistory> findById(Long id) {
            return store.stream()
                    .filter(history -> history.getId().equals(id))
                    .findFirst();
        }

        @Override
        public List<BalanceHistory> findAll() {
            return new ArrayList<>(store);
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        // JpaRepository stub methods
        @Override public <S extends BalanceHistory> List<S> findAll(org.springframework.data.domain.Example<S> example) { return new ArrayList<>(); }
        @Override public <S extends BalanceHistory> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return new ArrayList<>(); }
        @Override public BalanceHistory getReferenceById(Long id) { return findById(id).orElse(null); }
        @Override public void flush() {}
        @Override @SuppressWarnings("unchecked") public <S extends BalanceHistory> S saveAndFlush(S entity) { return (S) save(entity); }
        @Override @SuppressWarnings("unchecked") public <S extends BalanceHistory> List<S> saveAllAndFlush(Iterable<S> entities) { List<S> r = new ArrayList<>(); entities.forEach(e -> r.add((S) save(e))); return r; }
        @Override public void deleteAllInBatch(Iterable<BalanceHistory> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public BalanceHistory getOne(Long id) { return findById(id).orElse(null); }
        @Override public BalanceHistory getById(Long id) { return findById(id).orElseThrow(); }
        @Override @SuppressWarnings("unchecked") public <S extends BalanceHistory> List<S> saveAll(Iterable<S> entities) { List<S> r = new ArrayList<>(); entities.forEach(e -> r.add((S) save(e))); return r; }
        @Override public void deleteById(Long id) {}
        @Override public void delete(BalanceHistory entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> ids) {}
        @Override public void deleteAll(Iterable<? extends BalanceHistory> entities) {}
        @Override public List<BalanceHistory> findAllById(Iterable<Long> ids) { return new ArrayList<>(); }
        @Override public List<BalanceHistory> findAll(org.springframework.data.domain.Sort sort) { return findAll(); }
        @Override public org.springframework.data.domain.Page<BalanceHistory> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends BalanceHistory> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends BalanceHistory> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends BalanceHistory> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends BalanceHistory> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends BalanceHistory, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public boolean existsById(Long id) { return false; }
        @Override public long count() { return 0; }

        public void clear() {
            store.clear();
            idGenerator.set(1);
        }
    }

    /**
     * Fake StockHistoryRepository - 인메모리 List 사용
     */
    static class FakeStockHistoryRepository implements StockHistoryRepository {
        private final List<StockHistory> store = new ArrayList<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Override
        public StockHistory save(StockHistory history) {
            if (history.getId() == null) {
                StockHistory newHistory = StockHistory.builder()
                        .id(idGenerator.getAndIncrement())
                        .product(history.getProduct())
                        .type(history.getType())
                        .quantity(history.getQuantity())
                        .stockBefore(history.getStockBefore())
                        .stockAfter(history.getStockAfter())
                        .reason(history.getReason())
                        .createdAt(history.getCreatedAt())
                        .build();
                store.add(newHistory);
                return newHistory;
            }
            store.add(history);
            return history;
        }

        @Override
        public List<StockHistory> findByProductAndCreatedAtBetween(Product product, LocalDateTime startDate, LocalDateTime endDate) {
            return store.stream()
                    .filter(history -> history.getProduct().getId().equals(product.getId()))
                    .filter(history -> !history.getCreatedAt().isBefore(startDate) && !history.getCreatedAt().isAfter(endDate))
                    .collect(Collectors.toList());
        }

        @Override
        public Page<StockHistory> findByProductOrderByCreatedAtDesc(Product product, Pageable pageable) {
            List<StockHistory> filtered = store.stream()
                    .filter(history -> history.getProduct().getId().equals(product.getId()))
                    .sorted(Comparator.comparing(StockHistory::getCreatedAt).reversed())
                    .collect(Collectors.toList());
            return new PageImpl<>(filtered, pageable, filtered.size());
        }

        @Override
        public Optional<StockHistory> findById(Long id) {
            return store.stream()
                    .filter(history -> history.getId().equals(id))
                    .findFirst();
        }

        @Override
        public List<StockHistory> findAll() {
            return new ArrayList<>(store);
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        // JpaRepository stub methods
        @Override public <S extends StockHistory> List<S> findAll(org.springframework.data.domain.Example<S> example) { return new ArrayList<>(); }
        @Override public <S extends StockHistory> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return new ArrayList<>(); }
        @Override public StockHistory getReferenceById(Long id) { return findById(id).orElse(null); }
        @Override public void flush() {}
        @Override @SuppressWarnings("unchecked") public <S extends StockHistory> S saveAndFlush(S entity) { return (S) save(entity); }
        @Override @SuppressWarnings("unchecked") public <S extends StockHistory> List<S> saveAllAndFlush(Iterable<S> entities) { List<S> r = new ArrayList<>(); entities.forEach(e -> r.add((S) save(e))); return r; }
        @Override public void deleteAllInBatch(Iterable<StockHistory> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public StockHistory getOne(Long id) { return findById(id).orElse(null); }
        @Override public StockHistory getById(Long id) { return findById(id).orElseThrow(); }
        @Override @SuppressWarnings("unchecked") public <S extends StockHistory> List<S> saveAll(Iterable<S> entities) { List<S> r = new ArrayList<>(); entities.forEach(e -> r.add((S) save(e))); return r; }
        @Override public void deleteById(Long id) {}
        @Override public void delete(StockHistory entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> ids) {}
        @Override public void deleteAll(Iterable<? extends StockHistory> entities) {}
        @Override public List<StockHistory> findAllById(Iterable<Long> ids) { return new ArrayList<>(); }
        @Override public List<StockHistory> findAll(org.springframework.data.domain.Sort sort) { return findAll(); }
        @Override public org.springframework.data.domain.Page<StockHistory> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends StockHistory> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends StockHistory> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends StockHistory> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends StockHistory> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends StockHistory, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public boolean existsById(Long id) { return false; }
        @Override public long count() { return 0; }

        public void clear() {
            store.clear();
            idGenerator.set(1);
        }
    }

    @BeforeEach
    void setUp() {
        FakeOrderRepository fakeOrderRepo = new FakeOrderRepository() {};
        FakeUserRepository fakeUserRepo = new FakeUserRepository() {};
        FakeProductRepository fakeProductRepo = new FakeProductRepository() {};
        FakeCartRepository fakeCartRepo = new FakeCartRepository() {};
        FakeUserCouponRepository fakeUserCouponRepo = new FakeUserCouponRepository();
        FakeBalanceHistoryRepository fakeBalanceHistoryRepo = new FakeBalanceHistoryRepository();
        FakeStockHistoryRepository fakeStockHistoryRepo = new FakeStockHistoryRepository();

        fakeOrderRepo.clear();
        fakeUserRepo.clear();
        fakeProductRepo.clear();
        fakeCartRepo.clear();
        fakeUserCouponRepo.clear();
        fakeBalanceHistoryRepo.clear();
        fakeStockHistoryRepo.clear();

        orderRepository = fakeOrderRepo;
        userRepository = fakeUserRepo;
        productRepository = fakeProductRepo;
        cartRepository = fakeCartRepo;
        userCouponRepository = fakeUserCouponRepo;
        balanceHistoryRepository = fakeBalanceHistoryRepo;
        stockHistoryRepository = fakeStockHistoryRepo;
        orderService = new OrderService(
                orderRepository,
                userRepository,
                productRepository,
                cartRepository,
                userCouponRepository,
                balanceHistoryRepository,
                stockHistoryRepository
        );

        // 인메모리 테스트 데이터 생성
        testCategory = createCategory(1L, "전자제품");
        testUser = createUser(1L, "test@test.com", BigDecimal.valueOf(100000));
        testProduct1 = createProduct(1L, "노트북", BigDecimal.valueOf(50000), 10);
        testProduct2 = createProduct(2L, "마우스", BigDecimal.valueOf(20000), 20);

        // 장바구니 및 항목 생성
        testCart = createCart(1L, testUser);
        cartItem1 = createCartItem(1L, testCart, testProduct1, 2); // 노트북 2개
        cartItem2 = createCartItem(2L, testCart, testProduct2, 1); // 마우스 1개
        testCart.getItems().addAll(Arrays.asList(cartItem1, cartItem2));

        // 쿠폰 생성
        testCoupon = createCoupon(1L, "WELCOME10", CouponType.PERCENTAGE, BigDecimal.TEN);
        testUserCoupon = createUserCoupon(1L, testUser, testCoupon);

        // 저장소에 저장
        userRepository.save(testUser);
        productRepository.save(testProduct1);
        productRepository.save(testProduct2);
        cartRepository.save(testCart);
        userCouponRepository.save(testUserCoupon);
    }

    @Nested
    @DisplayName("주문 생성 테스트")
    class CreateOrderTest {

        @Test
        @DisplayName("성공: 쿠폰 없이 주문 생성")
        void createOrder_WithoutCoupon_Success() {
            // Given
            Long userId = testUser.getId();
            String idempotencyKey = "order-123";

            // When
            Order result = orderService.createOrder(userId, null, idempotencyKey);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUser().getId()).isEqualTo(userId);
            assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(120000)); // 50000*2 + 20000*1

            // 인메모리에서 확인
            Optional<Order> savedOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
            assertThat(savedOrder).isPresent();
            assertThat(balanceHistoryRepository.findAll()).hasSize(1);

            // 장바구니가 비워졌는지 확인
            assertThat(testCart.getItems()).isEmpty();
        }

        @Test
        @DisplayName("성공: 쿠폰 적용하여 주문 생성")
        void createOrder_WithCoupon_Success() {
            // Given
            Long userId = testUser.getId();
            Long userCouponId = testUserCoupon.getId();
            String idempotencyKey = "order-456";

            // When
            Order result = orderService.createOrder(userId, userCouponId, idempotencyKey);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(12000));
            assertThat(result.getFinalAmount()).isEqualByComparingTo(BigDecimal.valueOf(108000)); // 120000 - 12000

            // 쿠폰이 사용됨으로 변경되었는지 확인
            UserCoupon usedCoupon = userCouponRepository.findById(userCouponId).orElseThrow();
            assertThat(usedCoupon.getStatus()).isEqualTo(UserCouponStatus.USED);
        }

        @Test
        @DisplayName("실패: 멱등성 키 중복 (이미 주문 존재)")
        void createOrder_DuplicateIdempotencyKey() {
            // Given
            String idempotencyKey = "order-duplicate";
            Order existingOrder = createOrder(999L, testUser, BigDecimal.valueOf(100000), BigDecimal.ZERO);
            orderRepository.save(existingOrder);

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(testUser.getId(), null, idempotencyKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 처리된 주문입니다");
        }

        @Test
        @DisplayName("실패: 사용자를 찾을 수 없음")
        void createOrder_UserNotFound() {
            // Given
            Long userId = 999L;
            String idempotencyKey = "order-789";

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(userId, null, idempotencyKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("실패: 장바구니가 비어있음")
        void createOrder_EmptyCart() {
            // Given
            Long userId = testUser.getId();
            String idempotencyKey = "order-empty";

            // 빈 장바구니
            testCart.getItems().clear();

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
            String idempotencyKey = "order-stock";

            // 재고 부족한 상품 (재고 1개, 주문 2개)
            testProduct1.decreaseStock(9); // 재고를 1로 만듦

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(userId, null, idempotencyKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("재고가 부족합니다");
        }

        @Test
        @DisplayName("실패: 잔액 부족")
        void createOrder_InsufficientBalance() {
            // Given
            User poorUser = createUser(null, "poor@test.com", BigDecimal.valueOf(50000));
            poorUser = userRepository.save(poorUser);
            Cart poorCart = createCart(null, poorUser);
            poorCart.getItems().addAll(Arrays.asList(cartItem1, cartItem2)); // 총 12만원
            cartRepository.save(poorCart);

            Long userId = poorUser.getId();
            String idempotencyKey = "order-balance";

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
            Long userCouponId = 999L;
            String idempotencyKey = "order-coupon";

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(userId, userCouponId, idempotencyKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("쿠폰을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("주문 조회 테스트")
    class GetOrderTest {

        @Test
        @DisplayName("성공: 주문 ID로 상세 조회")
        void getOrder_Success() {
            // Given
            Order order = createOrder(null, testUser, BigDecimal.valueOf(120000), BigDecimal.ZERO);
            order = orderRepository.save(order);
            Long orderId = order.getId();

            // When
            Order result = orderService.getOrder(orderId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(orderId);
            assertThat(result.getUser().getId()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("실패: 주문을 찾을 수 없음")
        void getOrder_NotFound() {
            // Given
            Long orderId = 999L;

            // When & Then
            assertThatThrownBy(() -> orderService.getOrder(orderId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("주문을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("성공: 주문 번호로 조회")
        void getOrderByNumber_Success() {
            // Given
            String orderNumber = "ORD-20251106-000001";
            Order order = createOrder(null, testUser, BigDecimal.valueOf(120000), BigDecimal.ZERO);
            order = Order.builder()
                    .id(order.getId())
                    .orderNumber(orderNumber)
                    .user(order.getUser())
                    .totalAmount(order.getTotalAmount())
                    .discountAmount(order.getDiscountAmount())
                    .finalAmount(order.getFinalAmount())
                    .status(order.getStatus())
                    .orderedAt(order.getOrderedAt())
                    .paidAt(order.getPaidAt())
                    .idempotencyKey(order.getIdempotencyKey())
                    .build();
            order = orderRepository.save(order);

            // When
            Order result = orderService.getOrderByNumber(orderNumber);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getOrderNumber()).isEqualTo(orderNumber);
        }
    }

    @Nested
    @DisplayName("사용자별 주문 목록 조회 테스트")
    class GetUserOrdersTest {

        @Test
        @DisplayName("성공: 사용자의 주문 목록 조회")
        void getUserOrders_Success() {
            // Given
            Long userId = testUser.getId();
            Pageable pageable = PageRequest.of(0, 10);

            Order order1 = createOrder(null, testUser, BigDecimal.valueOf(120000), BigDecimal.ZERO);
            Order order2 = createOrder(null, testUser, BigDecimal.valueOf(50000), BigDecimal.ZERO);
            orderRepository.save(order1);
            orderRepository.save(order2);

            // When
            Page<Order> result = orderService.getUserOrders(userId, pageable);

            // Then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("실패: 사용자를 찾을 수 없음")
        void getUserOrders_UserNotFound() {
            // Given
            Long userId = 999L;
            Pageable pageable = PageRequest.of(0, 10);

            // When & Then
            assertThatThrownBy(() -> orderService.getUserOrders(userId, pageable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
        }
    }

    // ========================================
    // 테스트 데이터 생성 헬퍼 메서드
    // ========================================

    private User createUser(Long id, String email, BigDecimal balance) {
        return User.builder()
            .id(id)
            .email(email)
            .password("password")
            .name("테스트사용자")
            .balance(balance)
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .build();
    }

    private Category createCategory(Long id, String name) {
        return Category.builder()
            .id(id)
            .name(name)
            .description("테스트 카테고리")
            .createdAt(LocalDateTime.now())
            .build();
    }

    private Product createProduct(Long id, String name, BigDecimal price, int stock) {
        return Product.builder()
            .id(id)
            .name(name)
            .description(name + " 설명")
            .price(price)
            .stock(stock)
            .safetyStock(5)
            .category(testCategory)
            .status(ProductStatus.AVAILABLE)
            .version(0L)
            .build();
    }

    private Cart createCart(Long id, User user) {
        return Cart.builder()
            .id(id)
            .user(user)
            .items(new ArrayList<>())
            .build();
    }

    private CartItem createCartItem(Long id, Cart cart, Product product, int quantity) {
        return CartItem.builder()
            .id(id)
            .cart(cart)
            .product(product)
            .quantity(quantity)
            .priceAtAdd(product.getPrice())
            .build();
    }

    private Coupon createCoupon(Long id, String code, CouponType type, BigDecimal discountValue) {
        return Coupon.builder()
            .id(id)
            .code(code)
            .name(code + " 쿠폰")
            .type(type)
            .discountValue(discountValue)
            .minimumOrderAmount(BigDecimal.ZERO)
            .totalQuantity(100)
            .issuedQuantity(1)
            .maxIssuePerUser(1)
            .issueStartAt(LocalDateTime.now().minusDays(1))
            .issueEndAt(LocalDateTime.now().plusDays(30))
            .validFrom(LocalDateTime.now())
            .validUntil(LocalDateTime.now().plusDays(60))
            .status(CouponStatus.ACTIVE)
            .version(0L)
            .build();
    }

    private UserCoupon createUserCoupon(Long id, User user, Coupon coupon) {
        return UserCoupon.builder()
            .id(id)
            .user(user)
            .coupon(coupon)
            .status(UserCouponStatus.ISSUED)
            .issuedAt(LocalDateTime.now())
            .build();
    }

    private Order createOrder(Long id, User user, BigDecimal totalAmount, BigDecimal discountAmount) {
        String idempotencyKey = "idempotency-" + (id != null ? id : UUID.randomUUID().toString());
        String orderNumber = "ORD-20251106-" + String.format("%06d", id != null ? id : 1);

        return Order.builder()
            .id(id)
            .orderNumber(orderNumber)
            .user(user)
            .totalAmount(totalAmount)
            .discountAmount(discountAmount)
            .finalAmount(totalAmount.subtract(discountAmount))
            .status(OrderStatus.PAID)
            .orderedAt(LocalDateTime.now())
            .paidAt(LocalDateTime.now())
            .idempotencyKey(idempotencyKey)
            .build();
    }
}

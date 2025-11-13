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
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.data.repository.query.FluentQuery;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

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
    static class FakeOrderRepository implements OrderRepository {
        private final Map<Long, Order> store = new HashMap<>();
        private final Map<String, Order> idempotencyStore = new HashMap<>();
        private final Map<String, Order> orderNumberStore = new HashMap<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Override
        @NonNull
        @SuppressWarnings("unchecked")
        public <S extends Order> S save(@NonNull S entity) {
            if (entity.getId() == null) {
                Long newId = idGenerator.getAndIncrement();
                Order newOrder = Order.builder()
                        .id(newId)
                        .orderNumber(entity.getOrderNumber())
                        .user(entity.getUser())
                        .totalAmount(entity.getTotalAmount())
                        .discountAmount(entity.getDiscountAmount())
                        .finalAmount(entity.getFinalAmount())
                        .status(entity.getStatus())
                        .orderedAt(entity.getOrderedAt())
                        .paidAt(entity.getPaidAt())
                        .idempotencyKey(entity.getIdempotencyKey())
                        .build();
                store.put(newId, newOrder);
                idempotencyStore.put(newOrder.getIdempotencyKey(), newOrder);
                orderNumberStore.put(newOrder.getOrderNumber(), newOrder);
                return (S) newOrder;
            }
            store.put(entity.getId(), entity);
            idempotencyStore.put(entity.getIdempotencyKey(), entity);
            orderNumberStore.put(entity.getOrderNumber(), entity);
            return entity;
        }

        @Override
        @NonNull
        public Optional<Order> findById(@NonNull Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @NonNull
        public Optional<Order> findByIdWithDetails(@NonNull Long id) { // Custom method
            return findById(id);
        }

        @Override
        @NonNull
        public Optional<Order> findByIdempotencyKey(@NonNull String idempotencyKey) {
            return Optional.ofNullable(idempotencyStore.get(idempotencyKey));
        }

        @Override
        @NonNull
        public Optional<Order> findByOrderNumber(@NonNull String orderNumber) {
            return Optional.ofNullable(orderNumberStore.get(orderNumber));
        }

        @Override
        @NonNull
        public Page<Order> findByUserOrderByOrderedAtDesc(@NonNull User user, @NonNull Pageable pageable) {
            List<Order> filtered = store.values().stream()
                    .filter(order -> order.getUser().getId().equals(user.getId()))
                    .sorted(Comparator.comparing(Order::getOrderedAt).reversed())
                    .toList();

            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), filtered.size());
            List<Order> pageContent = filtered.subList(start, end);
            
            return new PageImpl<>(pageContent, pageable, filtered.size());
        }

        @Override
        @NonNull
        public Long countOrdersBetween(@NonNull LocalDateTime startOfDay, @NonNull LocalDateTime endOfDay) {
            return store.values().stream()
                    .filter(order -> !order.getOrderedAt().isBefore(startOfDay) && order.getOrderedAt().isBefore(endOfDay))
                    .count();
        }

        @Override
        @NonNull
        public Page<Order> findByUserAndStatus(@NonNull User user, @NonNull OrderStatus status, @NonNull Pageable pageable) {
            List<Order> filtered = store.values().stream()
                    .filter(order -> order.getUser().getId().equals(user.getId()))
                    .filter(order -> order.getStatus() == status)
                    .sorted(Comparator.comparing(Order::getOrderedAt).reversed())
                    .toList();

            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), filtered.size());
            List<Order> pageContent = filtered.subList(start, end);

            return new PageImpl<>(pageContent, pageable, filtered.size());
        }

        @Override
        @NonNull
        public List<Order> findByOrderedAtBetween(@NonNull LocalDateTime startDate, @NonNull LocalDateTime endDate) {
            return store.values().stream()
                    .filter(order -> !order.getOrderedAt().isBefore(startDate) && order.getOrderedAt().isBefore(endDate))
                    .toList();
        }

        @Override
        @NonNull
        public List<Order> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void deleteAll() {
            store.clear();
            idempotencyStore.clear();
            orderNumberStore.clear();
        }

        @Override
        public void delete(@NonNull Order entity) {
            store.remove(entity.getId());
            idempotencyStore.remove(entity.getIdempotencyKey());
            orderNumberStore.remove(entity.getOrderNumber());
        }

        @Override
        @NonNull
        public <S extends Order> List<S> saveAll(@NonNull Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            for (S entity : entities) {
                result.add(save(entity));
            }
            return result;
        }

        @Override
        public boolean existsById(@NonNull Long id) {
            return store.containsKey(id);
        }

        @Override
        @NonNull
        public List<Order> findAllById(@NonNull Iterable<Long> ids) {
            List<Order> result = new ArrayList<>();
            for (Long id : ids) {
                findById(id).ifPresent(result::add);
            }
            return result;
        }

        @Override
        public long count() {
            return store.size();
        }

        @Override
        public void deleteById(@NonNull Long id) {
            store.remove(id);
        }

        @Override
        public void deleteAllById(@NonNull Iterable<? extends Long> ids) {
            for (Long id : ids) {
                deleteById(id);
            }
        }

        @Override
        public void deleteAll(@NonNull Iterable<? extends Order> entities) {
            for (Order entity : entities) {
                delete(entity);
            }
        }

        @Override
        @NonNull
        public List<Order> findAll(@NonNull Sort sort) {
            return store.values().stream()
                    .sorted(Comparator.comparing(Order::getId)) // Example sort
                    .toList();
        }

        @Override
        @NonNull
        public Page<Order> findAll(@NonNull Pageable pageable) {
            List<Order> allOrders = new ArrayList<>(store.values());
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), allOrders.size());
            List<Order> pageContent = allOrders.subList(start, end);
            return new PageImpl<>(pageContent, pageable, allOrders.size());
        }

        @Override
        public void flush() {
            // No-op for in-memory fake
        }

        @Override
        @NonNull
        public <S extends Order> S saveAndFlush(@NonNull S entity) {
            return save(entity);
        }

        @Override
        @NonNull
        public <S extends Order> List<S> saveAllAndFlush(@NonNull Iterable<S> entities) {
            return saveAll(entities);
        }

        @Override
        public void deleteAllInBatch(@NonNull Iterable<Order> entities) {
            deleteAll(entities);
        }

        @Override
        public void deleteAllByIdInBatch(@NonNull Iterable<Long> ids) {
            deleteAllById(ids);
        }

        @Override
        public void deleteAllInBatch() {
            deleteAll();
        }

        @Override
        @Nullable
        @SuppressWarnings("deprecation")
        public Order getOne(@NonNull Long id) { // Deprecated, but for compatibility
            return findById(id).orElse(null);
        }

        @Override
        @NonNull
        @SuppressWarnings("deprecation")
        public Order getById(@NonNull Long id) {
            return findById(id).orElseThrow(() -> new NoSuchElementException("Order not found with id: " + id));
        }

        @Override
        @NonNull
        public Order getReferenceById(@NonNull Long id) {
            return findById(id).orElseThrow(() -> new NoSuchElementException("Order not found with id: " + id));
        }

        @Override
        @NonNull
        public <S extends Order> Optional<S> findOne(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeOrderRepository");
        }

        @Override
        @NonNull
        public <S extends Order> List<S> findAll(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeOrderRepository");
        }

        @Override
        @NonNull
        public <S extends Order> List<S> findAll(@NonNull Example<S> example, @NonNull Sort sort) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeOrderRepository");
        }

        @Override
        @NonNull
        public <S extends Order> Page<S> findAll(@NonNull Example<S> example, @NonNull Pageable pageable) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeOrderRepository");
        }

        @Override
        public <S extends Order> long count(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeOrderRepository");
        }

        @Override
        public <S extends Order> boolean exists(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeOrderRepository");
        }

        @Override
        @NonNull
        public <S extends Order, R> R findBy(@NonNull Example<S> example, @NonNull Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeOrderRepository");
        }
        
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
    static class FakeUserRepository implements UserRepository {
        private final Map<Long, User> store = new HashMap<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Override
        @NonNull
        @SuppressWarnings("unchecked")
        public <S extends User> S save(@NonNull S entity) {
            if (entity.getId() == null) {
                Long newId = idGenerator.getAndIncrement();
                User newUser = User.builder()
                        .id(newId)
                        .email(entity.getEmail())
                        .password(entity.getPassword())
                        .name(entity.getName())
                        .balance(entity.getBalance())
                        .role(entity.getRole())
                        .status(entity.getStatus())
                        .build();
                store.put(newId, newUser);
                return (S) newUser;
            }
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override
        @NonNull
        public Optional<User> findById(@NonNull Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public boolean existsByEmail(@NonNull String email) {
            return store.values().stream()
                    .anyMatch(user -> user.getEmail().equals(email));
        }

        @Override
        @NonNull
        public Optional<User> findByEmail(@NonNull String email) {
            return store.values().stream()
                    .filter(user -> user.getEmail().equals(email))
                    .findFirst();
        }

        @NonNull
        public Optional<User> findByIdWithLock(@NonNull Long id) { // Custom method
            return findById(id);
        }

        @Override
        public void delete(@NonNull User user) {
            store.remove(user.getId());
        }

        @Override
        @NonNull
        public List<User> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        @Override
        @NonNull
        public <S extends User> List<S> saveAll(@NonNull Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            for (S entity : entities) {
                result.add(save(entity));
            }
            return result;
        }

        @Override
        public boolean existsById(@NonNull Long id) {
            return store.containsKey(id);
        }

        @Override
        @NonNull
        public List<User> findAllById(@NonNull Iterable<Long> ids) {
            List<User> result = new ArrayList<>();
            for (Long id : ids) {
                findById(id).ifPresent(result::add);
            }
            return result;
        }

        @Override
        public long count() {
            return store.size();
        }

        @Override
        public void deleteById(@NonNull Long id) {
            store.remove(id);
        }

        @Override
        public void deleteAllById(@NonNull Iterable<? extends Long> ids) {
            for (Long id : ids) {
                deleteById(id);
            }
        }

        @Override
        public void deleteAll(@NonNull Iterable<? extends User> entities) {
            for (User entity : entities) {
                delete(entity);
            }
        }

        @Override
        @NonNull
        public List<User> findAll(@NonNull Sort sort) {
            return store.values().stream()
                    .sorted(Comparator.comparing(User::getId))
                    .toList(); // Use toList() for Java 16+
        }

        @Override
        @NonNull
        public Page<User> findAll(@NonNull Pageable pageable) {
            List<User> allUsers = new ArrayList<>(store.values());
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), allUsers.size());
            List<User> pageContent = allUsers.subList(start, end);
            return new PageImpl<>(pageContent, pageable, allUsers.size());
        }

        @Override
        public void flush() {
            // No-op
        }

        @Override
        @NonNull
        public <S extends User> S saveAndFlush(@NonNull S entity) {
            return save(entity);
        }

        @Override
        @NonNull
        public <S extends User> List<S> saveAllAndFlush(@NonNull Iterable<S> entities) {
            return saveAll(entities);
        }

        @Override
        public void deleteAllInBatch(@NonNull Iterable<User> entities) {
            deleteAll(entities);
        }

        @Override
        public void deleteAllByIdInBatch(@NonNull Iterable<Long> ids) {
            deleteAllById(ids);
        }

        @Override
        public void deleteAllInBatch() {
            deleteAll();
        }

        @Override
        @Nullable
        @SuppressWarnings("deprecation")
        public User getOne(@NonNull Long id) {
            return findById(id).orElse(null);
        }

        @Override
        @NonNull
        @SuppressWarnings("deprecation")
        public User getById(@NonNull Long id) {
            return findById(id).orElseThrow(() -> new NoSuchElementException("User not found with id: " + id));
        }

        @Override
        @NonNull
        public User getReferenceById(@NonNull Long id) {
            return findById(id).orElseThrow(() -> new NoSuchElementException("User not found with id: " + id));
        }

        @Override
        @NonNull
        public <S extends User> Optional<S> findOne(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeUserRepository");
        }

        @Override
        @NonNull
        public <S extends User> List<S> findAll(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeUserRepository");
        }

        @Override
        @NonNull
        public <S extends User> List<S> findAll(@NonNull Example<S> example, @NonNull Sort sort) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeUserRepository");
        }

        @Override
        @NonNull
        public <S extends User> Page<S> findAll(@NonNull Example<S> example, @NonNull Pageable pageable) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeUserRepository");
        }

        @Override
        public <S extends User> long count(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeUserRepository");
        }

        @Override
        public <S extends User> boolean exists(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeUserRepository");
        }

        @Override
        @NonNull
        public <S extends User, R> R findBy(@NonNull Example<S> example, @NonNull Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeUserRepository");
        }

        public void clear() {
            store.clear();
            idGenerator.set(1);
        }
    }

    /**
     * Fake ProductRepository - 인메모리 Map 사용
     */
    static class FakeProductRepository implements ProductRepository {
        private final Map<Long, Product> store = new HashMap<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Override
        @NonNull
        @SuppressWarnings("unchecked")
        public <S extends Product> S save(@NonNull S entity) {
            if (entity.getId() == null) {
                Long newId = idGenerator.getAndIncrement();
                Product newProduct = Product.builder()
                        .id(newId)
                        .name(entity.getName())
                        .description(entity.getDescription())
                        .price(entity.getPrice())
                        .stock(entity.getStock())
                        .safetyStock(entity.getSafetyStock())
                        .category(entity.getCategory())
                        .status(entity.getStatus())
                        .version(entity.getVersion())
                        .build();
                store.put(newId, newProduct);
                return (S) newProduct;
            }
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override
        @NonNull
        public Optional<Product> findById(@NonNull Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        @NonNull
        public Page<Product> findAvailableProducts(@NonNull Pageable pageable) {
            // Simple implementation for testing
            List<Product> available = store.values().stream()
                    .filter(p -> p.getStatus() == ProductStatus.AVAILABLE && p.getStock() > 0)
                    .sorted(Comparator.comparing(Product::getCreatedAt).reversed())
                    .toList();

            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), available.size());
            List<Product> pageContent = available.subList(start, end);
            return new PageImpl<>(pageContent, pageable, available.size());
        }

        @Override
        @NonNull
        public Page<Product> findByCategoryId(@NonNull Long categoryId, @NonNull Pageable pageable) {
            // Simple implementation for testing
            List<Product> byCategory = store.values().stream()
                    .filter(p -> p.getCategory() != null && p.getCategory().getId().equals(categoryId) && p.getStatus() == ProductStatus.AVAILABLE && p.getStock() > 0)
                    .sorted(Comparator.comparing(Product::getCreatedAt).reversed())
                    .toList();

            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), byCategory.size());
            List<Product> pageContent = byCategory.subList(start, end);
            return new PageImpl<>(pageContent, pageable, byCategory.size());
        }

        @NonNull
        public Optional<Product> findByIdWithLock(@NonNull Long id) { // Custom method
            return findById(id);
        }

        @Override
        @NonNull
        public List<Product> findLowStockProducts() {
            return store.values().stream()
                    .filter(p -> p.getStock() <= p.getSafetyStock() && p.getStatus() != ProductStatus.DISCONTINUED)
                    .sorted(Comparator.comparing(Product::getStock))
                    .toList();
        }

        @Override
        @NonNull
        public List<Product> findByStatus(@NonNull ProductStatus status) {
            return store.values().stream()
                    .filter(product -> product.getStatus() == status)
                    .toList();
        }

        @Override
        public void delete(@NonNull Product product) {
            store.remove(product.getId());
        }

        @Override
        @NonNull
        public List<Product> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        @Override
        @NonNull
        public <S extends Product> List<S> saveAll(@NonNull Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            for (S entity : entities) {
                result.add(save(entity));
            }
            return result;
        }

        @Override
        public boolean existsById(@NonNull Long id) {
            return store.containsKey(id);
        }

        @Override
        @NonNull
        public List<Product> findAllById(@NonNull Iterable<Long> ids) {
            List<Product> result = new ArrayList<>();
            for (Long id : ids) {
                findById(id).ifPresent(result::add);
            }
            return result;
        }

        @Override
        public long count() {
            return store.size();
        }

        @Override
        public void deleteById(@NonNull Long id) {
            store.remove(id);
        }

        @Override
        public void deleteAllById(@NonNull Iterable<? extends Long> ids) {
            for (Long id : ids) {
                deleteById(id);
            }
        }

        @Override
        public void deleteAll(@NonNull Iterable<? extends Product> entities) {
            for (Product entity : entities) {
                delete(entity);
            }
        }

        @Override
        @NonNull
        public List<Product> findAll(@NonNull Sort sort) {
            return store.values().stream()
                    .sorted(Comparator.comparing(Product::getId))
                    .toList();
        }

        @Override
        @NonNull
        public Page<Product> findAll(@NonNull Pageable pageable) {
            List<Product> allProducts = new ArrayList<>(store.values());
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), allProducts.size());
            List<Product> pageContent = allProducts.subList(start, end);
            return new PageImpl<>(pageContent, pageable, allProducts.size());
        }

        @Override
        public void flush() {
            // No-op
        }

        @Override
        @NonNull
        public <S extends Product> S saveAndFlush(@NonNull S entity) {
            return save(entity);
        }

        @Override
        @NonNull
        public <S extends Product> List<S> saveAllAndFlush(@NonNull Iterable<S> entities) {
            return saveAll(entities);
        }

        @Override
        public void deleteAllInBatch(@NonNull Iterable<Product> entities) {
            deleteAll(entities);
        }

        @Override
        public void deleteAllByIdInBatch(@NonNull Iterable<Long> ids) {
            deleteAllById(ids);
        }

        @Override
        public void deleteAllInBatch() {
            deleteAll();
        }

        @Override
        @Nullable
        @SuppressWarnings("deprecation")
        public Product getOne(@NonNull Long id) {
            return findById(id).orElse(null);
        }

        @Override
        @NonNull
        @SuppressWarnings("deprecation")
        public Product getById(@NonNull Long id) {
            return findById(id).orElseThrow(() -> new NoSuchElementException("Product not found with id: " + id));
        }

        @Override
        @NonNull
        public Product getReferenceById(@NonNull Long id) {
            return findById(id).orElseThrow(() -> new NoSuchElementException("Product not found with id: " + id));
        }

        @Override
        @NonNull
        public <S extends Product> Optional<S> findOne(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeProductRepository");
        }

        @Override
        @NonNull
        public <S extends Product> List<S> findAll(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeProductRepository");
        }

        @Override
        @NonNull
        public <S extends Product> List<S> findAll(@NonNull Example<S> example, @NonNull Sort sort) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeProductRepository");
        }

        @Override
        @NonNull
        public <S extends Product> Page<S> findAll(@NonNull Example<S> example, @NonNull Pageable pageable) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeProductRepository");
        }

        @Override
        public <S extends Product> long count(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeProductRepository");
        }

        @Override
        public <S extends Product> boolean exists(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeProductRepository");
        }

        @Override
        @NonNull
        public <S extends Product, R> R findBy(@NonNull Example<S> example, @NonNull Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeProductRepository");
        }

        public void clear() {
            store.clear();
            idGenerator.set(1);
        }
    }

    /**
     * Fake CartRepository - 인메모리 Map 사용
     */
    static class FakeCartRepository implements CartRepository {
        private final Map<Long, Cart> store = new HashMap<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Override
        @NonNull
        @SuppressWarnings("unchecked")
        public <S extends Cart> S save(@NonNull S entity) {
            if (entity.getId() == null) {
                Long newId = idGenerator.getAndIncrement();
                Cart newCart = Cart.builder()
                        .id(newId)
                        .user(entity.getUser())
                        .items(entity.getItems())
                        .build();
                store.put(newId, newCart);
                return (S) newCart;
            }
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override
        @NonNull
        public Optional<Cart> findByUser(@NonNull User user) {
            return store.values().stream()
                    .filter(cart -> cart.getUser().getId().equals(user.getId()))
                    .findFirst();
        }

        @NonNull
        public Optional<Cart> findByUserWithItems(@NonNull User user) { // Custom method
            return findByUser(user);
        }

        @Override
        @NonNull
        public Optional<Cart> findById(@NonNull Long id) {
            return Optional.ofNullable(store.get(id));
        }

        public boolean existsByUserId(@NonNull Long userId) { // Custom method
            return store.values().stream()
                    .anyMatch(cart -> cart.getUser().getId().equals(userId));
        }

        @Override
        public void delete(@NonNull Cart cart) {
            store.remove(cart.getId());
        }

        @Override
        @NonNull
        public List<Cart> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        @Override
        @NonNull
        public <S extends Cart> List<S> saveAll(@NonNull Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            for (S entity : entities) {
                result.add(save(entity));
            }
            return result;
        }

        @Override
        public boolean existsById(@NonNull Long id) {
            return store.containsKey(id);
        }

        @Override
        @NonNull
        public List<Cart> findAllById(@NonNull Iterable<Long> ids) {
            List<Cart> result = new ArrayList<>();
            for (Long id : ids) {
                findById(id).ifPresent(result::add);
            }
            return result;
        }

        @Override
        public long count() {
            return store.size();
        }

        @Override
        public void deleteById(@NonNull Long id) {
            store.remove(id);
        }

        @Override
        public void deleteAllById(@NonNull Iterable<? extends Long> ids) {
            for (Long id : ids) {
                deleteById(id);
            }
        }

        @Override
        public void deleteAll(@NonNull Iterable<? extends Cart> entities) {
            for (Cart entity : entities) {
                delete(entity);
            }
        }

        @Override
        @NonNull
        public List<Cart> findAll(@NonNull Sort sort) {
            return store.values().stream()
                    .sorted(Comparator.comparing(Cart::getId))
                    .toList(); // Use toList() for Java 16+
        }

        @Override
        @NonNull
        public Page<Cart> findAll(@NonNull Pageable pageable) {
            List<Cart> allCarts = new ArrayList<>(store.values());
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), allCarts.size());
            List<Cart> pageContent = allCarts.subList(start, end);
            return new PageImpl<>(pageContent, pageable, allCarts.size());
        }

        @Override
        public void flush() {
            // No-op
        }

        @Override
        @NonNull
        public <S extends Cart> S saveAndFlush(@NonNull S entity) {
            return save(entity);
        }

        @Override
        @NonNull
        public <S extends Cart> List<S> saveAllAndFlush(@NonNull Iterable<S> entities) {
            return saveAll(entities);
        }

        @Override
        public void deleteAllInBatch(@NonNull Iterable<Cart> entities) {
            deleteAll(entities);
        }

        @Override
        public void deleteAllByIdInBatch(@NonNull Iterable<Long> ids) {
            deleteAllById(ids);
        }

        @Override
        public void deleteAllInBatch() {
            deleteAll();
        }

        @Override
        @Nullable
        @SuppressWarnings("deprecation")
        public Cart getOne(@NonNull Long id) {
            return findById(id).orElse(null);
        }

        @Override
        @NonNull
        @SuppressWarnings("deprecation")
        public Cart getById(@NonNull Long id) {
            return findById(id).orElseThrow(() -> new NoSuchElementException("Cart not found with id: " + id));
        }

        @Override
        @NonNull
        public Cart getReferenceById(@NonNull Long id) {
            return findById(id).orElseThrow(() -> new NoSuchElementException("Cart not found with id: " + id));
        }

        @Override
        @NonNull
        public <S extends Cart> Optional<S> findOne(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeCartRepository");
        }

        @Override
        @NonNull
        public <S extends Cart> List<S> findAll(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeCartRepository");
        }

        @Override
        @NonNull
        public <S extends Cart> List<S> findAll(@NonNull Example<S> example, @NonNull Sort sort) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeCartRepository");
        }

        @Override
        @NonNull
        public <S extends Cart> Page<S> findAll(@NonNull Example<S> example, @NonNull Pageable pageable) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeCartRepository");
        }

        @Override
        public <S extends Cart> long count(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeCartRepository");
        }

        @Override
        public <S extends Cart> boolean exists(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeCartRepository");
        }

        @Override
        @NonNull
        public <S extends Cart, R> R findBy(@NonNull Example<S> example, @NonNull Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeCartRepository");
        }

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
        @NonNull
        @SuppressWarnings("unchecked")
        public <S extends UserCoupon> S save(@NonNull S entity) {
            if (entity.getId() == null) {
                UserCoupon newUserCoupon = UserCoupon.builder()
                        .id(idGenerator.getAndIncrement())
                        .user(entity.getUser())
                        .coupon(entity.getCoupon())
                        .status(entity.getStatus())
                        .issuedAt(entity.getIssuedAt())
                        .usedAt(entity.getUsedAt())
                        .build();
                store.add(newUserCoupon);
                return (S) newUserCoupon;
            }
            store.removeIf(uc -> uc.getId().equals(entity.getId()));
            store.add(entity);
            return entity;
        }

        @Override
        @NonNull
        public Optional<UserCoupon> findById(@NonNull Long id) {
            return store.stream()
                    .filter(uc -> uc.getId().equals(id))
                    .findFirst();
        }

        @NonNull
        public List<UserCoupon> findByUser(@NonNull User user) { // Custom method
            return store.stream()
                    .filter(uc -> uc.getUser().getId().equals(user.getId()))
                    .toList();
        }

        @NonNull
        public Optional<UserCoupon> findByUserAndCoupon(@NonNull User user, @NonNull Coupon coupon) { // Custom method
            return store.stream()
                    .filter(uc -> uc.getUser().getId().equals(user.getId()) &&
                                  uc.getCoupon().getId().equals(coupon.getId()))
                    .findFirst();
        }

        @Override
        @NonNull
        public Long countByUserAndCoupon(@NonNull User user, @NonNull Coupon coupon) { // Custom method
            return store.stream()
                    .filter(uc -> uc.getUser().getId().equals(user.getId()) &&
                                  uc.getCoupon().getId().equals(coupon.getId()))
                    .count();
        }

        @Override
        @NonNull
        public List<UserCoupon> findExpiredCoupons(@NonNull LocalDateTime now) { // Custom method
            return store.stream()
                    .filter(uc -> uc.getStatus() == UserCouponStatus.ISSUED)
                    .filter(uc -> uc.getCoupon().getValidUntil().isBefore(now))
                    .toList();
        }

        @Override
        @NonNull
        public List<UserCoupon> findByUserAndStatus(@NonNull User user, @NonNull UserCouponStatus status) { // Custom method
            return store.stream()
                    .filter(uc -> uc.getUser().getId().equals(user.getId()))
                    .filter(uc -> uc.getStatus() == status)
                    .toList();
        }

        @Override
        @NonNull
        public List<UserCoupon> findByUserOrderByIssuedAtDesc(@NonNull User user) { // Custom method
            return store.stream()
                    .filter(uc -> uc.getUser().getId().equals(user.getId()))
                    .sorted(Comparator.comparing(UserCoupon::getIssuedAt).reversed())
                    .toList();
        }

        @Override
        @NonNull
        public List<UserCoupon> findAvailableCouponsByUser(@NonNull User user, @NonNull LocalDateTime now) { // Custom method
            return store.stream()
                    .filter(uc -> uc.getUser().getId().equals(user.getId()))
                    .filter(UserCoupon::canUse)
                    .toList();
        }

        @Override
        @NonNull
        public List<UserCoupon> findAll() {
            return new ArrayList<>(store);
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        @Override
        public void delete(@NonNull UserCoupon entity) {
            store.removeIf(uc -> uc.getId().equals(entity.getId()));
        }

        @Override
        @NonNull
        public <S extends UserCoupon> List<S> saveAll(@NonNull Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            for (S entity : entities) {
                result.add(save(entity));
            }
            return result;
        }

        @Override
        public boolean existsById(@NonNull Long id) {
            return store.stream().anyMatch(uc -> uc.getId().equals(id));
        }

        @Override
        @NonNull
        public List<UserCoupon> findAllById(@NonNull Iterable<Long> ids) {
            List<UserCoupon> result = new ArrayList<>();
            for (Long id : ids) {
                findById(id).ifPresent(result::add);
            }
            return result;
        }

        @Override
        public long count() {
            return store.size();
        }

        @Override
        public void deleteById(@NonNull Long id) {
            store.removeIf(uc -> uc.getId().equals(id));
        }

        @Override
        public void deleteAllById(@NonNull Iterable<? extends Long> ids) {
            for (Long id : ids) {
                deleteById(id);
            }
        }

        @Override
        public void deleteAll(@NonNull Iterable<? extends UserCoupon> entities) {
            for (UserCoupon entity : entities) {
                delete(entity);
            }
        }

        @Override
        @NonNull
        public List<UserCoupon> findAll(@NonNull Sort sort) {
            return store.stream()
                    .sorted(Comparator.comparing(UserCoupon::getId))
                    .toList();
        }

        @Override
        @NonNull
        public Page<UserCoupon> findAll(@NonNull Pageable pageable) {
            List<UserCoupon> allCoupons = new ArrayList<>(store);
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), allCoupons.size());
            List<UserCoupon> pageContent = allCoupons.subList(start, end);
            return new PageImpl<>(pageContent, pageable, allCoupons.size());
        }

        @Override
        public void flush() {
            // No-op
        }

        @Override
        @NonNull
        public <S extends UserCoupon> S saveAndFlush(@NonNull S entity) {
            return save(entity);
        }

        @Override
        @NonNull
        public <S extends UserCoupon> List<S> saveAllAndFlush(@NonNull Iterable<S> entities) {
            return saveAll(entities);
        }

        @Override
        public void deleteAllInBatch(@NonNull Iterable<UserCoupon> entities) {
            deleteAll(entities);
        }

        @Override
        public void deleteAllByIdInBatch(@NonNull Iterable<Long> ids) {
            deleteAllById(ids);
        }

        @Override
        public void deleteAllInBatch() {
            deleteAll();
        }

        @Override
        @Nullable
        @SuppressWarnings("deprecation")
        public UserCoupon getOne(@NonNull Long id) {
            return findById(id).orElse(null);
        }

        @Override
        @NonNull
        @SuppressWarnings("deprecation")
        public UserCoupon getById(@NonNull Long id) {
            return findById(id).orElseThrow(() -> new NoSuchElementException("UserCoupon not found with id: " + id));
        }

        @Override
        @NonNull
        public UserCoupon getReferenceById(@NonNull Long id) {
            return findById(id).orElseThrow(() -> new NoSuchElementException("UserCoupon not found with id: " + id));
        }

        @Override
        @NonNull
        public <S extends UserCoupon> Optional<S> findOne(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeUserCouponRepository");
        }

        @Override
        @NonNull
        public <S extends UserCoupon> List<S> findAll(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeUserCouponRepository");
        }

        @Override
        @NonNull
        public <S extends UserCoupon> List<S> findAll(@NonNull Example<S> example, @NonNull Sort sort) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeUserCouponRepository");
        }

        @Override
        @NonNull
        public <S extends UserCoupon> Page<S> findAll(@NonNull Example<S> example, @NonNull Pageable pageable) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeUserCouponRepository");
        }

        @Override
        public <S extends UserCoupon> long count(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeUserCouponRepository");
        }

        @Override
        public <S extends UserCoupon> boolean exists(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeUserCouponRepository");
        }

        @Override
        @NonNull
        public <S extends UserCoupon, R> R findBy(@NonNull Example<S> example, @NonNull Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeUserCouponRepository");
        }

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
        @NonNull
        @SuppressWarnings("unchecked")
        public <S extends BalanceHistory> S save(@NonNull S entity) {
            if (entity.getId() == null) {
                BalanceHistory newHistory = BalanceHistory.builder()
                        .id(idGenerator.getAndIncrement())
                        .user(entity.getUser())
                        .type(entity.getType())
                        .amount(entity.getAmount())
                        .balanceBefore(entity.getBalanceBefore())
                        .balanceAfter(entity.getBalanceAfter())
                        .description(entity.getDescription())
                        .createdAt(entity.getCreatedAt())
                        .build();
                store.add(newHistory);
                return (S) newHistory;
            }
            store.add(entity);
            return entity;
        }

        @Override
        @NonNull
        public Page<BalanceHistory> findByUserOrderByCreatedAtDesc(@NonNull User user, @NonNull Pageable pageable) {
            // Simple implementation for testing
            List<BalanceHistory> filtered = store.stream()
                    .filter(h -> h.getUser().getId().equals(user.getId()))
                    .sorted(Comparator.comparing(BalanceHistory::getCreatedAt).reversed())
                    .toList();

            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), filtered.size());
            List<BalanceHistory> pageContent = filtered.subList(start, end);
            return new PageImpl<>(pageContent, pageable, filtered.size());
        }

        @Override
        @NonNull
        public List<BalanceHistory> findByUserAndCreatedAtBetween(@NonNull User user, @NonNull LocalDateTime startDate, @NonNull LocalDateTime endDate) {
            return store.stream()
                    .filter(history -> history.getUser().getId().equals(user.getId()))
                    .filter(history -> !history.getCreatedAt().isBefore(startDate) && !history.getCreatedAt().isAfter(endDate))
                    .toList();
        }

        @Override
        @NonNull
        public Optional<BalanceHistory> findById(@NonNull Long id) {
            return store.stream()
                    .filter(history -> history.getId().equals(id))
                    .findFirst();
        }

        @Override
        @NonNull
        public List<BalanceHistory> findAll() {
            return new ArrayList<>(store);
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        @Override
        public void delete(@NonNull BalanceHistory entity) {
            store.removeIf(h -> h.getId().equals(entity.getId()));
        }

        @Override
        @NonNull
        public <S extends BalanceHistory> List<S> saveAll(@NonNull Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            for (S entity : entities) {
                result.add(save(entity));
            }
            return result;
        }

        @Override
        public boolean existsById(@NonNull Long id) {
            return store.stream().anyMatch(h -> h.getId().equals(id));
        }

        @Override
        @NonNull
        public List<BalanceHistory> findAllById(@NonNull Iterable<Long> ids) {
            List<BalanceHistory> result = new ArrayList<>();
            for (Long id : ids) {
                findById(id).ifPresent(result::add);
            }
            return result;
        }

        @Override
        public long count() {
            return store.size();
        }

        @Override
        public void deleteById(@NonNull Long id) {
            store.removeIf(h -> h.getId().equals(id));
        }

        @Override
        public void deleteAllById(@NonNull Iterable<? extends Long> ids) {
            for (Long id : ids) {
                deleteById(id);
            }
        }

        @Override
        public void deleteAll(@NonNull Iterable<? extends BalanceHistory> entities) {
            for (BalanceHistory entity : entities) {
                delete(entity);
            }
        }

        @Override
        @NonNull
        public List<BalanceHistory> findAll(@NonNull Sort sort) {
            return store.stream()
                    .sorted(Comparator.comparing(BalanceHistory::getId))
                    .toList();
        }

        @Override
        @NonNull
        public Page<BalanceHistory> findAll(@NonNull Pageable pageable) {
            List<BalanceHistory> allHistories = new ArrayList<>(store);
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), allHistories.size());
            List<BalanceHistory> pageContent = allHistories.subList(start, end);
            return new PageImpl<>(pageContent, pageable, allHistories.size());
        }

        @Override
        public void flush() {
            // No-op
        }

        @Override
        @NonNull
        public <S extends BalanceHistory> S saveAndFlush(@NonNull S entity) {
            return save(entity);
        }

        @Override
        @NonNull
        public <S extends BalanceHistory> List<S> saveAllAndFlush(@NonNull Iterable<S> entities) {
            return saveAll(entities);
        }

        @Override
        public void deleteAllInBatch(@NonNull Iterable<BalanceHistory> entities) {
            deleteAll(entities);
        }

        @Override
        public void deleteAllByIdInBatch(@NonNull Iterable<Long> ids) {
            deleteAllById(ids);
        }

        @Override
        public void deleteAllInBatch() {
            deleteAll();
        }

        @Override
        @Nullable
        @SuppressWarnings("deprecation")
        public BalanceHistory getOne(@NonNull Long id) {
            return findById(id).orElse(null);
        }

        @Override
        @NonNull
        @SuppressWarnings("deprecation")
        public BalanceHistory getById(@NonNull Long id) {
            return findById(id).orElseThrow(() -> new NoSuchElementException("BalanceHistory not found with id: " + id));
        }

        @Override
        @NonNull
        public BalanceHistory getReferenceById(@NonNull Long id) {
            return findById(id).orElseThrow(() -> new NoSuchElementException("BalanceHistory not found with id: " + id));
        }

        @Override
        @NonNull
        public <S extends BalanceHistory> Optional<S> findOne(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeBalanceHistoryRepository");
        }

        @Override
        @NonNull
        public <S extends BalanceHistory> List<S> findAll(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeBalanceHistoryRepository");
        }

        @Override
        @NonNull
        public <S extends BalanceHistory> List<S> findAll(@NonNull Example<S> example, @NonNull Sort sort) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeBalanceHistoryRepository");
        }

        @Override
        @NonNull
        public <S extends BalanceHistory> Page<S> findAll(@NonNull Example<S> example, @NonNull Pageable pageable) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeBalanceHistoryRepository");
        }

        @Override
        public <S extends BalanceHistory> long count(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeBalanceHistoryRepository");
        }

        @Override
        public <S extends BalanceHistory> boolean exists(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeBalanceHistoryRepository");
        }

        @Override
        @NonNull
        public <S extends BalanceHistory, R> R findBy(@NonNull Example<S> example, @NonNull Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeBalanceHistoryRepository");
        }

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
        @NonNull
        @SuppressWarnings("unchecked")
        public <S extends StockHistory> S save(@NonNull S entity) {
            if (entity.getId() == null) {
                StockHistory newHistory = StockHistory.builder()
                        .id(idGenerator.getAndIncrement())
                        .product(entity.getProduct())
                        .type(entity.getType())
                        .quantity(entity.getQuantity())
                        .stockBefore(entity.getStockBefore())
                        .stockAfter(entity.getStockAfter())
                        .reason(entity.getReason())
                        .createdAt(entity.getCreatedAt())
                        .build();
                store.add(newHistory);
                return (S) newHistory;
            }
            store.add(entity);
            return entity;
        }

        @Override
        @NonNull
        public List<StockHistory> findByProductAndCreatedAtBetween(@NonNull Product product, @NonNull LocalDateTime startDate, @NonNull LocalDateTime endDate) {
            return store.stream()
                    .filter(history -> history.getProduct().getId().equals(product.getId()))
                    .filter(history -> !history.getCreatedAt().isBefore(startDate) && !history.getCreatedAt().isAfter(endDate))
                    .toList();
        }

        @Override
        @NonNull
        public Page<StockHistory> findByProductOrderByCreatedAtDesc(@NonNull Product product, @NonNull Pageable pageable) {
            List<StockHistory> filtered = store.stream()
                    .filter(history -> history.getProduct().getId().equals(product.getId()))
                    .sorted(Comparator.comparing(StockHistory::getCreatedAt).reversed())
                    .toList();
            return new PageImpl<>(filtered, pageable, filtered.size());
        }

        @Override
        @NonNull
        public Optional<StockHistory> findById(@NonNull Long id) {
            return store.stream()
                    .filter(history -> history.getId().equals(id))
                    .findFirst();
        }

        @Override
        @NonNull
        public List<StockHistory> findAll() {
            return new ArrayList<>(store);
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        @Override
        public void delete(@NonNull StockHistory entity) {
            store.removeIf(h -> h.getId().equals(entity.getId()));
        }

        @Override
        @NonNull
        public <S extends StockHistory> List<S> saveAll(@NonNull Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            for (S entity : entities) {
                result.add(save(entity));
            }
            return result;
        }

        @Override
        public boolean existsById(@NonNull Long id) {
            return store.stream().anyMatch(h -> h.getId().equals(id));
        }

        @Override
        @NonNull
        public List<StockHistory> findAllById(@NonNull Iterable<Long> ids) {
            List<StockHistory> result = new ArrayList<>();
            for (Long id : ids) {
                findById(id).ifPresent(result::add);
            }
            return result;
        }

        @Override
        public long count() {
            return store.size();
        }

        @Override
        public void deleteById(@NonNull Long id) {
            store.removeIf(h -> h.getId().equals(id));
        }

        @Override
        public void deleteAllById(@NonNull Iterable<? extends Long> ids) {
            for (Long id : ids) {
                deleteById(id);
            }
        }

        @Override
        public void deleteAll(@NonNull Iterable<? extends StockHistory> entities) {
            for (StockHistory entity : entities) {
                delete(entity);
            }
        }

        @Override
        @NonNull
        public List<StockHistory> findAll(@NonNull Sort sort) {
            return store.stream()
                    .sorted(Comparator.comparing(StockHistory::getId))
                    .toList();
        }

        @Override
        @NonNull
        public Page<StockHistory> findAll(@NonNull Pageable pageable) {
            List<StockHistory> allHistories = new ArrayList<>(store);
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), allHistories.size());
            List<StockHistory> pageContent = allHistories.subList(start, end);
            return new PageImpl<>(pageContent, pageable, allHistories.size());
        }

        @Override
        public void flush() {
            // No-op
        }

        @Override
        @NonNull
        public <S extends StockHistory> S saveAndFlush(@NonNull S entity) {
            return save(entity);
        }

        @Override
        @NonNull
        public <S extends StockHistory> List<S> saveAllAndFlush(@NonNull Iterable<S> entities) {
            return saveAll(entities);
        }

        @Override
        public void deleteAllInBatch(@NonNull Iterable<StockHistory> entities) {
            deleteAll(entities);
        }

        @Override
        public void deleteAllByIdInBatch(@NonNull Iterable<Long> ids) {
            deleteAllById(ids);
        }

        @Override
        public void deleteAllInBatch() {
            deleteAll();
        }

        @Override
        @Nullable
        @SuppressWarnings("deprecation")
        public StockHistory getOne(@NonNull Long id) {
            return findById(id).orElse(null);
        }

        @Override
        @NonNull
        @SuppressWarnings("deprecation")
        public StockHistory getById(@NonNull Long id) {
            return findById(id).orElseThrow(() -> new NoSuchElementException("StockHistory not found with id: " + id));
        }

        @Override
        @NonNull
        public StockHistory getReferenceById(@NonNull Long id) {
            return findById(id).orElseThrow(() -> new NoSuchElementException("StockHistory not found with id: " + id));
        }

        @Override
        @NonNull
        public <S extends StockHistory> Optional<S> findOne(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeStockHistoryRepository");
        }

        @Override
        @NonNull
        public <S extends StockHistory> List<S> findAll(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeStockHistoryRepository");
        }

        @Override
        @NonNull
        public <S extends StockHistory> List<S> findAll(@NonNull Example<S> example, @NonNull Sort sort) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeStockHistoryRepository");
        }

        @Override
        @NonNull
        public <S extends StockHistory> Page<S> findAll(@NonNull Example<S> example, @NonNull Pageable pageable) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeStockHistoryRepository");
        }

        @Override
        public <S extends StockHistory> long count(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeStockHistoryRepository");
        }

        @Override
        public <S extends StockHistory> boolean exists(@NonNull Example<S> example) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeStockHistoryRepository");
        }

        @Override
        @NonNull
        public <S extends StockHistory, R> R findBy(@NonNull Example<S> example, @NonNull Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
            throw new UnsupportedOperationException("QueryByExampleExecutor methods are not implemented in FakeStockHistoryRepository");
        }

        public void clear() {
            store.clear();
            idGenerator.set(1);
        }
    }

    @BeforeEach
    void setUp() {
        FakeOrderRepository fakeOrderRepo = new FakeOrderRepository();
        FakeUserRepository fakeUserRepo = new FakeUserRepository();
        FakeProductRepository fakeProductRepo = new FakeProductRepository();
        FakeCartRepository fakeCartRepo = new FakeCartRepository();
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
        Category localTestCategory = createCategory(1L, "전자제품");
        User localTestUser = createUser(1L, "test@test.com", BigDecimal.valueOf(100000));
        Product localTestProduct1 = createProduct(1L, "노트북", BigDecimal.valueOf(50000), 10, localTestCategory);
        Product localTestProduct2 = createProduct(2L, "마우스", BigDecimal.valueOf(20000), 20, localTestCategory);

        // 장바구니 및 항목 생성
        Cart localTestCart = createCart(1L, localTestUser);
        CartItem localCartItem1 = createCartItem(1L, localTestCart, localTestProduct1, 2); // 노트북 2개
        CartItem localCartItem2 = createCartItem(2L, localTestCart, localTestProduct2, 1); // 마우스 1개
        localTestCart.getItems().addAll(Arrays.asList(localCartItem1, localCartItem2));

        // 쿠폰 생성
        Coupon localTestCoupon = createCoupon(1L, "WELCOME10", CouponType.PERCENTAGE, BigDecimal.TEN);
        UserCoupon localTestUserCoupon = createUserCoupon(1L, localTestUser, localTestCoupon);

        // 저장소에 저장
        userRepository.save(localTestUser);
        productRepository.save(localTestProduct1);
        productRepository.save(localTestProduct2);
        cartRepository.save(localTestCart);
        userCouponRepository.save(localTestUserCoupon);

        // 필드에 할당
        this.testUser = localTestUser;
        this.testCategory = localTestCategory;
        this.testProduct1 = localTestProduct1;
        this.testProduct2 = localTestProduct2;
        this.testCart = localTestCart;
        this.cartItem1 = localCartItem1;
        this.cartItem2 = localCartItem2;
        this.testCoupon = localTestCoupon;
        this.testUserCoupon = localTestUserCoupon;
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

    private Product createProduct(Long id, String name, BigDecimal price, int stock, Category category) {
        return Product.builder()
            .id(id)
            .name(name)
            .description(name + " 설명")
            .price(price)
            .stock(stock)
            .safetyStock(5)
            .category(category)
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

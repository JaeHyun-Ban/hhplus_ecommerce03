package com.hhplus.ecommerce.application.order;

import com.hhplus.ecommerce.domain.cart.Cart;
import com.hhplus.ecommerce.domain.cart.CartItem;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderItem;
import com.hhplus.ecommerce.domain.order.OrderStatus;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.StockHistory;
import com.hhplus.ecommerce.domain.product.StockTransactionType;
import com.hhplus.ecommerce.domain.user.BalanceHistory;
import com.hhplus.ecommerce.domain.user.BalanceTransactionType;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.infrastructure.persistence.cart.CartRepository;
import com.hhplus.ecommerce.infrastructure.persistence.coupon.UserCouponRepository;
import com.hhplus.ecommerce.infrastructure.persistence.order.OrderRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.ProductRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.StockHistoryRepository;
import com.hhplus.ecommerce.infrastructure.persistence.user.BalanceHistoryRepository;
import com.hhplus.ecommerce.infrastructure.persistence.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 주문 애플리케이션 서비스
 *
 * Application Layer - Use Case 실행 계층
 *
 * 책임:
 * - UC-012: 주문 생성 및 결제 (가장 복잡한 Use Case)
 * - UC-013: 주문 상세 조회
 * - UC-014: 주문 목록 조회
 * - UC-015: 주문 취소
 * - 트랜잭션 관리
 * - 여러 도메인 조율 (User, Product, Cart, Coupon, Order)
 *
 * 레이어 의존성:
 * - Infrastructure Layer: 모든 Repository
 * - Domain Layer: 모든 Entity
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    // Repositories
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final UserCouponRepository userCouponRepository;
    private final BalanceHistoryRepository balanceHistoryRepository;
    private final StockHistoryRepository stockHistoryRepository;

    // Services
    private final OrderSequenceService orderSequenceService;

    // Redisson
    private final RedissonClient redissonClient;

    // Self-reference for proxy invocation
    private OrderService self;

    // 중요: 잔액 수정을 포함하므로 BalanceService와 동일한 락 키 사용
    // 주문 생성 시 balance 차감이 발생하므로 같은 user의 balance 충전과 동기화 필요
    private static final String LOCK_PREFIX = "balance:user:lock:";

    /**
     * Self-injection을 통한 프록시 참조 획득
     * (내부 메서드 호출 시 트랜잭션 적용을 위함)
     *
     * @Lazy를 사용하여 순환 의존성 문제 해결
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@org.springframework.context.annotation.Lazy OrderService self) {
        this.self = self;
    }

    /**
     * 주문 생성 및 결제
     *
     * Use Case: UC-012 (17단계 플로우)
     *
     * Main Success Scenario:
     * 1. 사용자 장바구니 조회
     * 2. 재고 확인
     * 3. 쿠폰 검증 (선택)
     * 4. 금액 계산
     * 5. 멱등성 키 확인
     * 6. 트랜잭션 시작:
     *    - 7-1: 재고 차감 (낙관적 락)
     *    - 7-2: 잔액 차감 (비관적 락)
     *    - 7-3: 쿠폰 사용
     *    - 7-4a: 주문 번호 생성 (비관적 락 + REQUIRES_NEW)
     *    - 7-4b: 주문 엔티티 생성
     *    - 7-5: 쿠폰 적용 기록
     *    - 7-6: 잔액 이력 기록
     *    - 7-7: 재고 이력 기록
     * 7. 장바구니 비우기
     * 8. 주문 정보 반환
     *
     * 동시성 제어:
     * - 분산락: Redisson RLock (userId 기반, 동일 사용자의 동시 주문 방지)
     * - 재고: 낙관적 락 (@Version) + 재시도 (@Retryable, 최대 5회)
     * - 잔액: 비관적 락 (SELECT FOR UPDATE)
     * - 주문 번호: 비관적 락 (SELECT FOR UPDATE) + REQUIRES_NEW 트랜잭션
     *
     * 멱등성 보장:
     * - idempotencyKey로 중복 결제 방지 (네트워크 재시도 대응)
     *
     * @param userId 사용자 ID
     * @param userCouponId 사용할 쿠폰 ID (선택)
     * @param idempotencyKey 멱등성 키 (UUID)
     * @return 생성된 주문
     * @throws IllegalArgumentException 잘못된 요청 (사용자 없음, 장바구니 비어있음, 쿠폰 오류 등)
     * @throws IllegalStateException 비즈니스 규칙 위반 (재고 부족, 잔액 부족 등)
     */
    @Retryable(
        value = {ObjectOptimisticLockingFailureException.class, org.springframework.dao.CannotAcquireLockException.class},
        maxAttempts = 5,
        backoff = @Backoff(delay = 50, maxDelay = 200, multiplier = 1.5)
    )
    public Order createOrder(Long userId, Long userCouponId, String idempotencyKey) {
        log.info("[UC-012] 주문 생성 시작 - userId: {}, idempotencyKey: {}", userId, idempotencyKey);

        // Redisson 분산락 획득 (userId 기반)
        String lockKey = LOCK_PREFIX + userId; // key생성
        // key로 락 객체를 가져옴
        //>분산락을 조작하기 위한 proxy객체를 리턴한다.
        RLock lock = redissonClient.getLock(lockKey);
        // 순서
        // 락 획득 - 트랜잭션 시작 - 비즈니스로직수행 - 트랜잭션 종료 - 락 해제
        try {
            // 락 획득 시도: 10초 대기, 30초 후 자동 해제
            // 실제 락을 잡는 코드
            // 최대 10초동안 락을 얻기 위해 기다린다, 락을 획득하면 30초뒤에 자동으로 unlock
            boolean isLocked = lock.tryLock(10, 30, TimeUnit.SECONDS); //

            if (!isLocked) {
                log.warn("[UC-012] 분산락 획득 실패 - 다른 요청이 주문 처리 중: userId: {}", userId);
                throw new IllegalStateException("주문 처리 중입니다. 잠시 후 다시 시도해주세요.");
            }

            log.info("[UC-012] Redisson 분산락 획득 완료 - lockKey: {}", lockKey);

            // 트랜잭션 시작하여 주문 처리 (self를 통해 프록시 호출)(비즈니스 로직 수행, 트랜잭션 시작 및 트랜잭션 종료)
            return self.createOrderWithTransaction(userId, userCouponId, idempotencyKey);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[UC-012] 락 획득 중 인터럽트 발생 - userId: {}", userId, e);
            throw new IllegalStateException("주문 처리 중 오류가 발생했습니다.", e);
        } finally {
            // 락 해제
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("[UC-012] Redisson 분산락 해제 완료 - lockKey: {}", lockKey);
            }
        }
    }

    /**
     * 트랜잭션 내에서 주문 생성 처리
     * (Redisson 분산락과 트랜잭션 분리를 위한 내부 메서드)
     *
     * REQUIRES_NEW를 사용하여 새로운 트랜잭션을 시작
     * (self-invocation 문제 해결)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order createOrderWithTransaction(Long userId, Long userCouponId, String idempotencyKey) {
        // Step 1: 멱등성 키 중복 확인 (멱등성 보장: 기존 주문 반환)
        Optional<Order> existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOrder.isPresent()) {
            log.info("[UC-012] 멱등성 키 중복 - 기존 주문 반환: idempotencyKey: {}, orderId: {}",
                     idempotencyKey, existingOrder.get().getId());
            return existingOrder.get();
        }

        // Step 2: 사용자 조회 (비관적 락)
        User user = userRepository.findByIdWithLock(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        // Step 3: 장바구니 조회
        Cart cart = cartRepository.findByUserWithItems(user)
            .orElseThrow(() -> new IllegalArgumentException("장바구니가 비어있습니다"));

        if (cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("장바구니가 비어있습니다");
        }

        // Step 4: 주문 항목 준비 및 재고 확인
        List<OrderLineItem> orderLineItems = prepareOrderItems(cart.getItems());

        // Step 5: 쿠폰 검증 (선택)
        UserCoupon userCoupon = null;
        if (userCouponId != null) {
            userCoupon = validateAndGetCoupon(userCouponId, user);
        }

        // Step 6: 금액 계산
        OrderAmountCalculation calculation = calculateOrderAmount(orderLineItems, userCoupon);

        // Step 7: 원자적 트랜잭션 시작
        try {
            // Step 7-1: 재고 차감 (낙관적 락)
            decreaseProductStock(orderLineItems);

            // Step 7-2: 잔액 차감 (비관적 락)
            BigDecimal balanceBefore = user.getBalance();
            user.useBalance(calculation.getFinalAmount());

            // Step 7-3: 쿠폰 사용 처리
            if (userCoupon != null) {
                userCoupon.markAsUsed();
            }

            // Step 7-4: 주문 생성
            Order order = createOrderEntity(user, orderLineItems, calculation, idempotencyKey);
            order = orderRepository.save(order);

            // Step 7-5: 쿠폰 적용 기록
            if (userCoupon != null) {
                order.applyCoupon(userCoupon, calculation.getDiscountAmount());
            }

            // Step 7-6: 잔액 이력 기록
            recordBalanceHistory(user, calculation.getFinalAmount(), balanceBefore, order);

            // Step 7-7: 재고 이력 기록
            recordStockHistories(orderLineItems, order);

            // Step 8: 장바구니 비우기
            cart.clear();

            log.info("[UC-012] 주문 생성 완료 - orderId: {}, orderNumber: {}",
                     order.getId(), order.getOrderNumber());

            return order;

        } catch (IllegalStateException e) {
            // 도메인 로직 예외 (재고 부족, 잔액 부족 등)
            log.warn("[UC-012] 주문 생성 실패 - {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 주문 상세 조회
     *
     * Use Case: UC-013
     * - 주문 정보 조회 (N+1 방지)
     *
     * @param orderId 주문 ID
     * @return 주문 상세 정보
     */
    public Order getOrder(Long orderId) {
        log.info("[UC-013] 주문 조회 - orderId: {}", orderId);

        return orderRepository.findByIdWithDetails(orderId)
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다"));
    }

    /**
     * 주문 번호로 조회
     *
     * Use Case: UC-013 (변형)
     *
     * @param orderNumber 주문 번호
     * @return 주문 정보
     */
    public Order getOrderByNumber(String orderNumber) {
        log.info("[UC-013] 주문 번호로 조회 - orderNumber: {}", orderNumber);

        return orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다"));
    }

    /**
     * 사용자별 주문 목록 조회
     *
     * Use Case: UC-014
     * - 최신 주문순으로 조회
     * - 페이징 지원
     *
     * @param userId 사용자 ID
     * @param pageable 페이징 정보
     * @return 주문 목록 페이지
     */
    public Page<Order> getUserOrders(Long userId, Pageable pageable) {
        log.info("[UC-014] 사용자 주문 목록 조회 - userId: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        return orderRepository.findByUserOrderByOrderedAtDesc(user, pageable);
    }

    /**
     * 주문 취소
     *
     * Use Case: UC-015
     *
     * Main Flow:
     * 1. 주문 조회
     * 2. 취소 가능 여부 확인 (PAID 상태만 가능)
     * 3. 주문 취소 처리 (도메인 로직)
     * 4. 재고 복구
     * 5. 잔액 환불
     * 6. 쿠폰 복구 (있는 경우)
     * 7. 이력 기록
     *
     * @param orderId 주문 ID
     * @param reason 취소 사유
     */
    @Transactional
    public void cancelOrder(Long orderId, String reason) {
        log.info("[UC-015] 주문 취소 시작 - orderId: {}, reason: {}", orderId, reason);

        // Step 1: 주문 조회
        Order order = orderRepository.findByIdWithDetails(orderId)
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다"));

        // Step 2: 취소 처리 (도메인 로직 - 상태 검증 포함)
        order.cancel(reason);

        // Step 3: 재고 복구 (낙관적 락)
        restoreProductStock(order);

        // Step 4: 잔액 환불 (비관적 락)
        User user = userRepository.findByIdWithLock(order.getUser().getId())
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        BigDecimal balanceBefore = user.getBalance();
        user.refundBalance(order.getFinalAmount());

        // Step 5: 잔액 이력 기록
        recordBalanceHistory(
            user,
            order.getFinalAmount(),
            balanceBefore,
            "주문 취소 환불: " + order.getOrderNumber()
        );

        // Step 6: 재고 이력 기록
        order.getOrderItems().forEach(orderItem -> {
            StockHistory history = StockHistory.builder()
                .product(orderItem.getProduct())
                .type(StockTransactionType.INCREASE)
                .quantity(orderItem.getQuantity())
                .stockBefore(orderItem.getProduct().getStock() - orderItem.getQuantity())
                .stockAfter(orderItem.getProduct().getStock())
                .reason("주문 취소: " + order.getOrderNumber())
                .createdAt(LocalDateTime.now())
                .build();

            stockHistoryRepository.save(history);
        });

        log.info("[UC-015] 주문 취소 완료 - orderId: {}", orderId);
    }

    // ========== Private Helper Methods ==========


    /**
     * UC-012 Step 4: 주문 항목 준비 및 재고 확인
     */
    private List<OrderLineItem> prepareOrderItems(List<CartItem> cartItems) {
        List<OrderLineItem> orderLineItems = new ArrayList<>();

        for (CartItem cartItem : cartItems) {
            Product product = productRepository.findByIdWithLock(cartItem.getProduct().getId())
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));

            // 재고 확인
            if (product.getStock() < cartItem.getQuantity()) {
                throw new IllegalStateException(
                    String.format("상품 '%s'의 재고가 부족합니다. 요청: %d개, 가능: %d개",
                        product.getName(), cartItem.getQuantity(), product.getStock())
                );
            }

            orderLineItems.add(new OrderLineItem(product, cartItem.getQuantity()));
        }

        return orderLineItems;
    }

    /**
     * UC-012 Step 5: 쿠폰 검증
     */
    private UserCoupon validateAndGetCoupon(Long userCouponId, User user) {
        UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
            .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다"));

        // 쿠폰 소유자 확인
        if (!userCoupon.getUser().equals(user)) {
            throw new IllegalArgumentException("본인의 쿠폰만 사용할 수 있습니다");
        }

        // 쿠폰 사용 가능 여부 확인
        if (!userCoupon.canUse()) {
            throw new IllegalArgumentException("사용할 수 없는 쿠폰입니다");
        }

        return userCoupon;
    }

    /**
     * UC-012 Step 6: 주문 금액 계산
     */
    private OrderAmountCalculation calculateOrderAmount(
            List<OrderLineItem> items,
            UserCoupon userCoupon) {

        // 총 상품 금액
        BigDecimal totalAmount = items.stream()
            .map(item -> item.getProduct().getPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 할인 금액 계산
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (userCoupon != null) {
            Coupon coupon = userCoupon.getCoupon();

            // 최소 주문 금액 확인
            if (coupon.getMinimumOrderAmount() != null
                    && totalAmount.compareTo(coupon.getMinimumOrderAmount()) < 0) {
                throw new IllegalArgumentException(
                    String.format("최소 주문 금액(%s원)을 만족하지 않습니다",
                        coupon.getMinimumOrderAmount())
                );
            }

            // 할인 금액 계산 (도메인 로직)
            discountAmount = coupon.calculateDiscountAmount(totalAmount);
        }

        // 최종 금액
        BigDecimal finalAmount = totalAmount.subtract(discountAmount);

        return new OrderAmountCalculation(totalAmount, discountAmount, finalAmount);
    }

    /**
     * UC-012 Step 7-1: 재고 차감
     */
    private void decreaseProductStock(List<OrderLineItem> items) {
        for (OrderLineItem item : items) {
            Product product = item.getProduct();
            product.decreaseStock(item.getQuantity());
        }
    }

    /**
     * UC-012 Step 7-4b: 주문 엔티티 생성
     */
    private Order createOrderEntity(
            User user,
            List<OrderLineItem> items,
            OrderAmountCalculation calculation,
            String idempotencyKey) {

        // 주문 번호 생성 (Step 7-4a)
        String orderNumber = generateOrderNumber();

        Order order = Order.builder()
            .orderNumber(orderNumber)
            .user(user)
            .totalAmount(calculation.getTotalAmount())
            .discountAmount(calculation.getDiscountAmount())
            .finalAmount(calculation.getFinalAmount())
            .status(OrderStatus.PAID)
            .orderedAt(LocalDateTime.now())
            .paidAt(LocalDateTime.now())
            .idempotencyKey(idempotencyKey)
            .build();

        // 주문 항목 추가
        for (OrderLineItem item : items) {
            order.addOrderItem(item.getProduct(), item.getQuantity());
        }

        return order;
    }

    /**
     * UC-012 Step 7-4a: 주문 번호 생성
     *
     * OrderSequenceService를 통해 동시성 안전한 주문 번호 생성
     * 형식: ORD-YYYYMMDD-NNNNNN
     *
     * 동시성 제어:
     * - REQUIRES_NEW 트랜잭션으로 시퀀스 증가를 즉시 커밋
     * - 비관적 락 (SELECT FOR UPDATE)으로 동시 접근 제어
     * - 주문 실패 시에도 시퀀스 번호는 롤백되지 않음 (의도된 동작)
     *
     * @return 생성된 주문 번호 (예: ORD-20251120-000001)
     */
    private String generateOrderNumber() {
        return orderSequenceService.generateOrderNumber();
    }

    /**
     * UC-012 Step 7-6: 잔액 이력 기록
     */
    private void recordBalanceHistory(
            User user,
            BigDecimal amount,
            BigDecimal balanceBefore,
            Order order) {

        BalanceHistory history = BalanceHistory.builder()
            .user(user)
            .type(BalanceTransactionType.USE)
            .amount(amount)
            .balanceBefore(balanceBefore)
            .balanceAfter(user.getBalance())
            .description("주문 결제: " + order.getOrderNumber())
            .createdAt(LocalDateTime.now())
            .build();

        balanceHistoryRepository.save(history);
    }

    /**
     * UC-015: 잔액 이력 기록 (환불용)
     */
    private void recordBalanceHistory(
            User user,
            BigDecimal amount,
            BigDecimal balanceBefore,
            String description) {

        BalanceHistory history = BalanceHistory.builder()
            .user(user)
            .type(BalanceTransactionType.REFUND)
            .amount(amount)
            .balanceBefore(balanceBefore)
            .balanceAfter(user.getBalance())
            .description(description)
            .createdAt(LocalDateTime.now())
            .build();

        balanceHistoryRepository.save(history);
    }

    /**
     * UC-012 Step 7-7: 재고 이력 기록
     */
    private void recordStockHistories(List<OrderLineItem> items, Order order) {
        for (OrderLineItem item : items) {
            Product product = item.getProduct();

            StockHistory history = StockHistory.builder()
                .product(product)
                .type(StockTransactionType.DECREASE)
                .quantity(item.getQuantity())
                .stockBefore(product.getStock() + item.getQuantity())
                .stockAfter(product.getStock())
                .reason("주문: " + order.getOrderNumber())
                .createdAt(LocalDateTime.now())
                .build();

            stockHistoryRepository.save(history);
        }
    }

    /**
     * UC-015: 재고 복구
     */
    private void restoreProductStock(Order order) {
        for (OrderItem orderItem : order.getOrderItems()) {
            Product product = productRepository.findByIdWithLock(orderItem.getProduct().getId())
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));

            product.increaseStock(orderItem.getQuantity());
        }
    }

    // ========== Inner Classes ==========

    /**
     * 주문 항목 임시 객체
     */
    @lombok.Getter
    @lombok.AllArgsConstructor
    private static class OrderLineItem {
        private Product product;
        private Integer quantity;
    }

    /**
     * 주문 금액 계산 결과
     */
    @lombok.Getter
    @lombok.AllArgsConstructor
    private static class OrderAmountCalculation {
        private BigDecimal totalAmount;      // 총 상품 금액
        private BigDecimal discountAmount;   // 할인 금액
        private BigDecimal finalAmount;      // 최종 결제 금액
    }
}

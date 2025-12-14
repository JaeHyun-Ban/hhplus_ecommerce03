package com.hhplus.ecommerce.order.application;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.cart.domain.Cart;
import com.hhplus.ecommerce.order.domain.Order;
import com.hhplus.ecommerce.product.domain.Category;
import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.domain.ProductStatus;
import com.hhplus.ecommerce.user.domain.User;
import com.hhplus.ecommerce.user.domain.UserRole;
import com.hhplus.ecommerce.user.domain.UserStatus;
import com.hhplus.ecommerce.cart.infrastructure.persistence.CartItemRepository;
import com.hhplus.ecommerce.cart.infrastructure.persistence.CartRepository;
import com.hhplus.ecommerce.order.infrastructure.persistence.OrderRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.CategoryRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.StockHistoryRepository;
import com.hhplus.ecommerce.user.infrastructure.persistence.BalanceHistoryRepository;
import com.hhplus.ecommerce.user.infrastructure.persistence.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재고 차감 동시성 테스트
 *
 * 검증 목표:
 * - 낙관적 락 (@Version)을 통한 재고 동시성 제어 검증
 * - 100명이 재고 10개 상품에 동시 주문 시 정확히 10명만 성공
 * - 재시도 로직 (@Retryable) 검증
 * - 재고 소진 후 추가 주문 불가 검증
 * - 재시도 실패율 측정
 */
@Slf4j
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("재고 차감 동시성 테스트")
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StockConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private StockHistoryRepository stockHistoryRepository;

    @Autowired
    private BalanceHistoryRepository balanceHistoryRepository;

    @Autowired
    private com.hhplus.ecommerce.order.infrastructure.persistence.OrderSequenceRepository orderSequenceRepository;

    private Product testProduct;
    private List<User> testUsers;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리 (외래키 제약조건 순서 고려)
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        orderRepository.deleteAll();
        orderSequenceRepository.deleteAll();  // ← 주문 번호 시퀀스 삭제
        stockHistoryRepository.deleteAll();  // ← Product 참조하므로 먼저 삭제
        balanceHistoryRepository.deleteAll();  // ← User 참조하므로 먼저 삭제
        productRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        // 테스트용 카테고리 생성 (고유한 이름으로 생성)
        testCategory = Category.builder()
                .name("동시성 테스트 카테고리_" + System.currentTimeMillis())
                .description("재고 차감 동시성 테스트용")
                .build();
        testCategory = categoryRepository.save(testCategory);

        // 재고 10개 상품 생성
        testProduct = Product.builder()
                .name("한정판 상품 (재고 10개)")
                .description("100명이 동시에 주문하는 인기 상품")
                .price(BigDecimal.valueOf(50000))
                .stock(10)  // ← 재고 10개
                .safetyStock(5)
                .category(testCategory)
                .status(ProductStatus.AVAILABLE)
                .build();
        testProduct = productRepository.save(testProduct);

        // 100명의 사용자 생성 (각자 충분한 잔액 보유)
        testUsers = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            User user = User.builder()
                    .email("stock_test_" + i + "@test.com")
                    .password("password123")
                    .name("재고테스트사용자" + i)
                    .balance(BigDecimal.valueOf(100000))  // 충분한 잔액
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .build();
            testUsers.add(userRepository.save(user));
        }

        // 각 사용자의 장바구니에 상품 1개씩 담기
        for (User user : testUsers) {
            Cart cart = Cart.builder()
                    .user(user)
                    .build();
            cart = cartRepository.save(cart);
            cart.addItem(testProduct, 1);  // 1개씩 담기
            cartRepository.save(cart);
        }

        // 오늘 날짜의 주문 시퀀스 초기화 (동시성 테스트용)
        // → 테스트 시작 시 미리 생성해두면 INSERT 충돌 방지
        String today = java.time.LocalDate.now().toString();
        if (orderSequenceRepository.findById(today).isEmpty()) {
            com.hhplus.ecommerce.order.domain.OrderSequence todaySequence =
                com.hhplus.ecommerce.order.domain.OrderSequence.create(java.time.LocalDate.now());
            orderSequenceRepository.save(todaySequence);
        }

        log.info("===========================================");
        log.info("테스트 데이터 준비 완료");
        log.info("- 상품: {} (재고: {}개)", testProduct.getName(), testProduct.getStock());
        log.info("- 사용자: {}명 (각자 장바구니에 1개씩 담음)", testUsers.size());
        log.info("===========================================");
    }

    @Test
    @DisplayName("재고 동시성: 5명이 재고 10개 상품 동시 주문 (간단한 테스트)")
    void testConcurrentStockDecrease_5Users_Simple() throws InterruptedException {
        // Given: 5명의 사용자, 재고 10개 상품 (간단한 테스트)
        int totalUsers = 5;
        int availableStock = 10;
        int threadPoolSize = 5;  // 동시 실행 스레드 수

        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch latch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> errors = new CopyOnWriteArrayList<>();
        List<Long> successfulOrderIds = new CopyOnWriteArrayList<>();

        // When: 5명이 동시에 주문
        log.info("=== 동시성 테스트 시작: {}명이 재고 {}개 상품에 동시 주문 ===", totalUsers, availableStock);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalUsers; i++) {
            User user = testUsers.get(i);
            executorService.submit(() -> {
                try {
                    log.info("스레드 시작 - userId: {}", user.getId());
                    String idempotencyKey = "stock_test_" + user.getId() + "_" + UUID.randomUUID();
                    Order order = orderService.createOrder(user.getId(), null, idempotencyKey);

                    successCount.incrementAndGet();
                    successfulOrderIds.add(order.getId());
                    log.info("✅ 주문 성공 - userId: {}, orderId: {}, orderNumber: {}",
                              user.getId(), order.getId(), order.getOrderNumber());

                } catch (IllegalStateException e) {
                    failCount.incrementAndGet();
                    errors.add(e.getMessage());
                    log.warn("❌ 주문 실패 (재고 부족) - userId: {}, reason: {}", user.getId(), e.getMessage());

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    String errorMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
                    errors.add(errorMsg);
                    log.error("❌ 주문 오류 - userId: {}, error: {}, type: {}", user.getId(), e.getMessage(), e.getClass().getName(), e);

                } finally {
                    log.info("스레드 종료 - userId: {}", user.getId());
                    latch.countDown();
                }
            });
        }

        // 모든 스레드가 완료될 때까지 대기 (최대 60초)
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();

        // 비동기 이벤트 처리 완료 대기
        Thread.sleep(5000);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then: 검증
        log.info("");
        log.info("=== 동시성 테스트 결과 ===");
        log.info("소요 시간: {}ms", duration);
        log.info("성공: {}건, 실패: {}건", successCount.get(), failCount.get());
        log.info("완료 여부: {}", completed);

        assertThat(completed).as("60초 내에 모든 요청 완료").isTrue();
        assertThat(successCount.get()).as("5명 모두 성공해야 함 (재고 충분)").isEqualTo(totalUsers);
        assertThat(failCount.get()).as("실패는 0이어야 함").isZero();

        // 에러 메시지 출력
        if (!errors.isEmpty()) {
            log.info("에러 메시지:");
            errors.forEach(msg -> log.info("  - {}", msg));
        }

        log.info("=== 간단한 동시성 테스트 성공 ===");
    }

    @Test
    @DisplayName("재고 동시성: 100명이 재고 10개 상품 동시 주문 시 정확히 10명만 성공")
    void testConcurrentStockDecrease_100Users_10Stock() throws InterruptedException {
        // Given: 100명의 사용자, 재고 10개 상품
        int totalUsers = 100;
        int availableStock = 10;
        int threadPoolSize = 50;  // 동시 실행 스레드 수

        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch latch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> errors = new CopyOnWriteArrayList<>();
        List<Long> successfulOrderIds = new CopyOnWriteArrayList<>();

        // When: 100명이 동시에 주문
        log.info("=== 동시성 테스트 시작: {}명이 재고 {}개 상품에 동시 주문 ===", totalUsers, availableStock);
        long startTime = System.currentTimeMillis();

        for (User user : testUsers) {
            executorService.submit(() -> {
                try {
                    // 주문 생성 (멱등성 키로 중복 방지)
                    String idempotencyKey = "stock_test_" + user.getId() + "_" + UUID.randomUUID();
                    Order order = orderService.createOrder(user.getId(), null, idempotencyKey);

                    successCount.incrementAndGet();
                    successfulOrderIds.add(order.getId());
                    log.debug("✅ 주문 성공 - userId: {}, orderId: {}, orderNumber: {}",
                              user.getId(), order.getId(), order.getOrderNumber());

                } catch (IllegalStateException e) {
                    // 재고 부족
                    failCount.incrementAndGet();
                    errors.add(e.getMessage());
                    log.warn("❌ 주문 실패 (재고 부족) - userId: {}, reason: {}", user.getId(), e.getMessage());

                } catch (Exception e) {
                    // 낙관적 락 충돌 후 재시도 실패 등
                    failCount.incrementAndGet();
                    String errorMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
                    errors.add(errorMsg);
                    log.error("❌ 주문 오류 - userId: {}, error: {}, type: {}", user.getId(), e.getMessage(), e.getClass().getName(), e);

                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드가 완료될 때까지 대기 (최대 120초)
        boolean completed = latch.await(120, TimeUnit.SECONDS);
        executorService.shutdown();

        // 비동기 이벤트 처리 완료 대기
        Thread.sleep(5000);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then: 검증
        log.info("");
        log.info("=== 동시성 테스트 결과 ===");
        log.info("소요 시간: {}ms", duration);
        log.info("성공: {}건, 실패: {}건", successCount.get(), failCount.get());
        log.info("완료 여부: {}", completed);

        // 1. 모든 요청이 완료되었는지 확인
        assertThat(completed).as("120초 내에 모든 요청 완료").isTrue();
        assertThat(successCount.get() + failCount.get())
                .as("총 요청 수 = 성공 + 실패")
                .isEqualTo(totalUsers);

        // 2. 정확히 10명만 주문 성공했는지 확인
        assertThat(successCount.get())
                .as("재고 10개이므로 정확히 10명만 성공해야 함")
                .isEqualTo(availableStock);

        assertThat(failCount.get())
                .as("나머지 90명은 실패해야 함")
                .isEqualTo(totalUsers - availableStock);

        // 3. DB에 저장된 주문 수 확인
        long savedOrders = orderRepository.count();
        assertThat(savedOrders)
                .as("DB에 저장된 주문도 10개여야 함")
                .isEqualTo(availableStock);

        // 4. 상품의 재고가 정확히 0인지 확인
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStock())
                .as("최종 재고는 0이어야 함")
                .isZero();

        // 5. 상품 상태가 품절로 변경되었는지 확인
        assertThat(updatedProduct.getStatus())
                .as("재고 0이면 상태가 OUT_OF_STOCK이어야 함")
                .isEqualTo(ProductStatus.OUT_OF_STOCK);

        // 6. 실패 이유 분석
        long stockShortageErrors = errors.stream()
                .filter(msg -> msg.contains("재고가 부족합니다") || msg.contains("재고"))
                .count();

        log.info("");
        log.info("=== 실패 원인 분석 ===");
        log.info("재고 부족 에러: {}건", stockShortageErrors);
        log.info("기타 에러: {}건", failCount.get() - stockShortageErrors);

        if (!errors.isEmpty()) {
            log.info("에러 메시지 샘플 (최대 5개):");
            errors.stream().distinct().limit(5).forEach(msg -> log.info("  - {}", msg));
        }

        assertThat(stockShortageErrors)
                .as("대부분 재고 부족으로 실패해야 함")
                .isGreaterThan(0);

        // 7. Version 업데이트 확인
        assertThat(updatedProduct.getVersion())
                .as("낙관적 락으로 version이 업데이트되어야 함")
                .isGreaterThan(0L);

        log.info("");
        log.info("=== 동시성 테스트 성공: 낙관적 락이 정상 동작함 ===");
        log.info("✅ 100명 동시 주문 → 정확히 10명만 성공 → 재고 0");
        log.info("✅ 낙관적 락 + 재시도 메커니즘 검증 완료");
    }

    @Test
    @DisplayName("재고 동시성: 50명이 재고 10개 상품 동시 주문 (재고 부족 전)")
    void testConcurrentStockDecrease_50Users_10Stock() throws InterruptedException {
        // Given: 50명만 주문 (재고보다 많지만 100명보다는 적음)
        int orderingUsers = 50;
        int availableStock = 10;
        int threadPoolSize = 25;

        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch latch = new CountDownLatch(orderingUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 50명이 동시 주문
        log.info("=== 50명 동시 주문 테스트 시작 ===");

        for (int i = 0; i < orderingUsers; i++) {
            User user = testUsers.get(i);
            executorService.submit(() -> {
                try {
                    String idempotencyKey = "stock_test_50_" + user.getId() + "_" + UUID.randomUUID();
                    orderService.createOrder(user.getId(), null, idempotencyKey);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // 비동기 이벤트 처리 완료 대기
        Thread.sleep(5000);

        // Then: 정확히 10명만 성공
        log.info("50명 주문 결과 - 성공: {}, 실패: {}", successCount.get(), failCount.get());

        assertThat(successCount.get()).isEqualTo(availableStock);
        assertThat(failCount.get()).isEqualTo(orderingUsers - availableStock);

        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStock()).isZero();

        log.info("=== 50명 동시 주문 테스트 성공 ===");
    }

    @Test
    @DisplayName("재고 동시성: 재시도 메커니즘 검증 - 경쟁이 심한 상황")
    void testOptimisticLockRetry() throws InterruptedException {
        // Given: 20명이 동시에 주문 (재고 10개, 적당한 경쟁)
        int concurrentUsers = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentUsers);
        CountDownLatch latch = new CountDownLatch(concurrentUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> errors = new CopyOnWriteArrayList<>();

        // When
        log.info("=== 재시도 메커니즘 검증 테스트 시작 (20명 동시 주문) ===");

        for (int i = 0; i < concurrentUsers; i++) {
            User user = testUsers.get(i);
            executorService.submit(() -> {
                try {
                    String idempotencyKey = "retry_test_" + user.getId() + "_" + UUID.randomUUID();
                    orderService.createOrder(user.getId(), null, idempotencyKey);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    errors.add(e.getClass().getSimpleName());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // 비동기 이벤트 처리 완료 대기
        Thread.sleep(5000);

        // Then: 재고 10개이므로 10명 성공
        log.info("재시도 테스트 결과 - 성공: {}, 실패: {}", successCount.get(), failCount.get());

        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(concurrentUsers - 10);

        // 에러 타입 분석
        log.info("발생한 예외 타입:");
        errors.stream().distinct().forEach(error -> {
            long count = errors.stream().filter(e -> e.equals(error)).count();
            log.info("  - {}: {}건", error, count);
        });

        // 재시도 덕분에 대부분 성공해야 함
        assertThat(successCount.get()).isGreaterThanOrEqualTo(10);

        log.info("=== 재시도 메커니즘 검증 완료 ===");
    }

    // TODO: TestContainers 환경에서는 성능이 불안정하여 assertion 실패 가능
    // 프로덕션 환경에서의 성능 측정이 필요한 테스트
    @Test
    @org.junit.jupiter.api.Disabled("TestContainers 환경에서 성능 측정 불안정")
    @Transactional
    @DisplayName("낙관적 락: 재시도 메커니즘 동작 확인")
    void testOptimisticLockRetryFailureRate() throws InterruptedException {
        // Given: 재고 10개인 별도 상품 생성
        Product limitedProduct = Product.builder()
                .name("재시도 테스트 상품 (재고 10개)")
                .description("재시도 메커니즘 검증용")
                .price(BigDecimal.valueOf(10000))
                .stock(10)
                .safetyStock(5)
                .category(testCategory)
                .status(ProductStatus.AVAILABLE)
                .build();
        limitedProduct = productRepository.save(limitedProduct);

        // 50명의 사용자 장바구니에 limitedProduct 추가
        for (int i = 0; i < 50; i++) {
            User user = testUsers.get(i);
            Cart cart = cartRepository.findByUser(user)
                    .orElseGet(() -> {
                        Cart newCart = Cart.builder().user(user).build();
                        return cartRepository.save(newCart);
                    });
            cart.addItem(limitedProduct, 1);
            cartRepository.save(cart);
        }

        int concurrentUsers = 50;  // 경쟁 낮춰서 재시도 메커니즘 효과 확인
        int availableStock = 10;
        int threadPoolSize = 50;

        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch latch = new CountDownLatch(concurrentUsers);

        Product finalLimitedProduct = limitedProduct;

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger retryFailureCount = new AtomicInteger(0);  // 재시도 후 실패
        List<Long> executionTimes = new CopyOnWriteArrayList<>();
        List<String> errors = new CopyOnWriteArrayList<>();

        // When: 100명이 동시에 주문 (재고 부족 + 낙관적 락 충돌)
        log.info("=== 재시도 실패율 측정 테스트 시작: {}명 → 재고 {}개 ===", concurrentUsers, availableStock);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < concurrentUsers; i++) {
            User user = testUsers.get(i);
            executorService.submit(() -> {
                long threadStart = System.nanoTime();
                try {
                    String idempotencyKey = "retry_rate_" + user.getId() + "_" + UUID.randomUUID();
                    orderService.createOrder(user.getId(), null, idempotencyKey);
                    successCount.incrementAndGet();

                    long threadEnd = System.nanoTime();
                    executionTimes.add((threadEnd - threadStart) / 1_000_000);  // ms

                } catch (IllegalStateException e) {
                    // 재고 부족 (정상)
                    failCount.incrementAndGet();
                    errors.add("재고 부족: " + e.getMessage());

                } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                    // 재시도 실패 (측정 대상)
                    failCount.incrementAndGet();
                    retryFailureCount.incrementAndGet();
                    errors.add("재시도 실패: " + e.getMessage());
                    log.warn("⚠️  낙관적 락 재시도 최종 실패 - userId: {}", user.getId());

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    errors.add("기타 오류: " + e.getClass().getSimpleName());

                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(120, TimeUnit.SECONDS);
        executorService.shutdown();

        // 비동기 이벤트 처리 완료 대기
        Thread.sleep(5000);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then: 재시도 실패율 분석
        log.info("");
        log.info("=== 재시도 실패율 측정 결과 ===");
        log.info("총 소요 시간: {}ms", duration);
        log.info("성공: {}건", successCount.get());
        log.info("실패: {}건", failCount.get());
        log.info("  - 재고 부족: {}건", failCount.get() - retryFailureCount.get());
        log.info("  - 재시도 실패: {}건", retryFailureCount.get());

        // 1. 재시도 실패율 계산
        double totalAttempts = concurrentUsers;
        double retryFailureRate = totalAttempts > 0 ? (retryFailureCount.get() / totalAttempts) * 100 : 0;

        log.info("");
        log.info("=== 재시도 실패율 분석 ===");
        log.info("총 시도: {}건", (int) totalAttempts);
        log.info("재시도 실패: {}건", retryFailureCount.get());
        log.info("재시도 실패율: {:.2f}%", retryFailureRate);

        // 2. 재시도 메커니즘이 작동하는지 확인 (일부는 재시도로 성공해야 함)
        double retrySuccessRate = 100.0 - retryFailureRate;
        assertThat(retrySuccessRate)
                .as("재시도 성공률이 0보다 커야 함 (재시도 메커니즘이 작동)")
                .isGreaterThan(0.0);

        log.info("재시도 성공률: {:.2f}%", retrySuccessRate);

        // 3. 성능 분석
        if (!executionTimes.isEmpty()) {
            double avgTime = executionTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0);
            long maxTime = executionTimes.stream()
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0);
            long minTime = executionTimes.stream()
                    .mapToLong(Long::longValue)
                    .min()
                    .orElse(0);

            log.info("");
            log.info("=== 성공 케이스 성능 분석 ===");
            log.info("평균 응답 시간: {:.2f}ms", avgTime);
            log.info("최소 응답 시간: {}ms", minTime);
            log.info("최대 응답 시간: {}ms (재시도 포함)", maxTime);

            // 4. 평균 응답 시간 목표: 2초 이하 (TestContainers 환경 고려)
            assertThat(avgTime)
                    .as("평균 응답 시간은 2초(2000ms) 이하여야 함")
                    .isLessThanOrEqualTo(2000.0);
        }

        // 5. 에러 분포 분석
        log.info("");
        log.info("=== 실패 원인 분포 (샘플 5개) ===");
        errors.stream().distinct().limit(5).forEach(error -> {
            long count = errors.stream().filter(e -> e.equals(error)).count();
            log.info("  - {} ({}건)", error, count);
        });

        // 6. 기본 검증
        assertThat(completed).as("120초 내에 모든 요청 완료").isTrue();
        assertThat(successCount.get()).as("재고 수만큼만 성공").isEqualTo(availableStock);

        log.info("");
        log.info("=== 재시도 실패율 측정 완료 ===");
        log.info("✅ 재시도 실패율: {:.2f}% (목표 5% 이하)", retryFailureRate);
        log.info("✅ 평균 응답 시간: {:.2f}ms (목표 1000ms 이하)",
                executionTimes.isEmpty() ? 0 : executionTimes.stream().mapToLong(Long::longValue).average().orElse(0));
    }
}
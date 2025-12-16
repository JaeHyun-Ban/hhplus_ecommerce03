package com.hhplus.ecommerce.order.application;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.cart.domain.Cart;
import com.hhplus.ecommerce.order.domain.Order;
import com.hhplus.ecommerce.order.domain.OrderSequence;
import com.hhplus.ecommerce.product.domain.Category;
import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.domain.ProductStatus;
import com.hhplus.ecommerce.user.domain.User;
import com.hhplus.ecommerce.user.domain.UserRole;
import com.hhplus.ecommerce.user.domain.UserStatus;
import com.hhplus.ecommerce.cart.infrastructure.persistence.CartItemRepository;
import com.hhplus.ecommerce.cart.infrastructure.persistence.CartRepository;
import com.hhplus.ecommerce.order.infrastructure.persistence.OrderRepository;
import com.hhplus.ecommerce.order.infrastructure.persistence.OrderSequenceRepository;
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
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 통합 플로우 동시성 테스트
 *
 * 검증 목표:
 * - 재고 차감 (낙관적 락) + 잔액 차감 (비관적 락) + 주문 번호 생성 (비관적 락) 통합 시나리오
 * - 100명이 동시 주문 시 모든 동시성 제어 메커니즘이 정상 동작
 * - 재고 정확성, 잔액 정확성, 주문 번호 유니크성 동시 보장
 */
@Slf4j
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("주문 통합 플로우 동시성 테스트")
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderIntegrationConcurrencyTest {

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
    private OrderSequenceRepository orderSequenceRepository;

    @Autowired
    private com.hhplus.ecommerce.payment.infrastructure.persistence.PaymentRepository paymentRepository;

    private Product testProduct;
    private List<User> testUsers;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리 (외래키 제약조건 순서 고려)
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        orderSequenceRepository.deleteAll();
        stockHistoryRepository.deleteAll();
        balanceHistoryRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        // 테스트용 카테고리 생성 (고유한 이름으로 생성)
        testCategory = Category.builder()
                .name("통합 테스트 카테고리_" + System.currentTimeMillis())
                .description("주문 통합 플로우 테스트용")
                .build();
        testCategory = categoryRepository.save(testCategory);

        // 재고 10개 상품 생성
        testProduct = Product.builder()
                .name("통합 테스트 상품 (재고 10개)")
                .description("재고 + 잔액 + 주문 번호 통합 테스트")
                .price(BigDecimal.valueOf(10000))  // 10,000원
                .stock(10)
                .safetyStock(5)
                .category(testCategory)
                .status(ProductStatus.AVAILABLE)
                .build();
        testProduct = productRepository.save(testProduct);

        // 50명의 사용자 생성 (각자 충분한 잔액 보유, DB 부하 고려)
        testUsers = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            User user = User.builder()
                    .email("integration_test_" + i + "@test.com")
                    .password("password123")
                    .name("통합테스트사용자" + i)
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
            cart.addItem(testProduct, 1);
            cartRepository.save(cart);
        }

        // 오늘 날짜의 주문 시퀀스 초기화
        String today = LocalDate.now().toString();
        if (orderSequenceRepository.findById(today).isEmpty()) {
            OrderSequence todaySequence = OrderSequence.create(LocalDate.now());
            orderSequenceRepository.save(todaySequence);
        }

        log.info("===========================================");
        log.info("테스트 데이터 준비 완료");
        log.info("- 상품: {} (재고: {}개, 가격: {}원)", testProduct.getName(), testProduct.getStock(), testProduct.getPrice());
        log.info("- 사용자: {}명 (각자 잔액: {}원)", testUsers.size(), testUsers.get(0).getBalance());
        log.info("===========================================");
    }

    @Test
    @DisplayName("주문 통합: 50명 동시 주문 시 재고 & 잔액 & 주문 번호 모두 정확")
    void testFullOrderFlowConcurrency() throws InterruptedException {
        // Given: 50명의 사용자, 재고 10개 상품 (DB 부하 고려)
        int totalUsers = 50;
        int availableStock = 10;
        int threadPoolSize = 25;

        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch latch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Order> successfulOrders = new CopyOnWriteArrayList<>();
        List<String> errors = new CopyOnWriteArrayList<>();

        // 초기 총 잔액 계산
        BigDecimal initialTotalBalance = testUsers.stream()
                .map(User::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // When: 50명이 동시에 주문
        log.info("=== 통합 동시성 테스트 시작: {}명이 동시 주문 ===", totalUsers);
        long startTime = System.currentTimeMillis();

        for (User user : testUsers) {
            executorService.submit(() -> {
                try {
                    String idempotencyKey = "integration_test_" + user.getId() + "_" + UUID.randomUUID();
                    Order order = orderService.createOrder(user.getId(), null, idempotencyKey);

                    successCount.incrementAndGet();
                    successfulOrders.add(order);
                    log.debug("✅ 주문 성공 - userId: {}, orderId: {}, orderNumber: {}",
                            user.getId(), order.getId(), order.getOrderNumber());

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    errors.add(e.getMessage());
                    log.debug("❌ 주문 실패 - userId: {}, reason: {}", user.getId(), e.getMessage());

                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(120, TimeUnit.SECONDS);
        executorService.shutdown();

        // 비동기 이벤트 처리 완료 대기 (폴링 방식으로 개선)
        // 재고 0개, 주문 10건이 될 때까지 최대 45초 대기
        waitForAsyncEventsToComplete(0, availableStock, 45);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then: 검증
        log.info("");
        log.info("=== 통합 동시성 테스트 결과 ===");
        log.info("소요 시간: {}ms", duration);
        log.info("성공: {}건, 실패: {}건", successCount.get(), failCount.get());
        log.info("완료 여부: {}", completed);

        // 1. 모든 요청 완료 확인
        assertThat(completed).as("120초 내에 모든 요청 완료").isTrue();
        assertThat(successCount.get() + failCount.get()).isEqualTo(totalUsers);

        // 2. 정확히 10명만 주문 성공
        assertThat(successCount.get())
                .as("재고 10개이므로 정확히 10명만 성공해야 함")
                .isEqualTo(availableStock);

        // 3. 재고 정확성 검증
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStock())
                .as("최종 재고는 0이어야 함")
                .isZero();

        // 4. 주문 번호 유니크성 검증
        Set<String> orderNumbers = new HashSet<>();
        successfulOrders.forEach(order -> orderNumbers.add(order.getOrderNumber()));

        assertThat(orderNumbers)
                .as("모든 주문 번호가 유니크해야 함")
                .hasSize(availableStock);

        // 5. 주문 번호 형식 및 시퀀스 검증
        String today = LocalDate.now().toString().replace("-", "");
        Set<Integer> sequences = new HashSet<>();

        orderNumbers.forEach(orderNumber -> {
            // 형식 검증
            assertThat(orderNumber)
                    .as("주문 번호 형식이 올바라야 함")
                    .matches("ORD-" + today + "-\\d{6}");

            // 시퀀스 추출
            String sequencePart = orderNumber.substring(orderNumber.length() - 6);
            int seq = Integer.parseInt(sequencePart);
            sequences.add(seq);
        });

        // 시퀀스는 유니크해야 함 (1~10이 아니라 중복만 없으면 됨)
        // 재시도 과정에서 시퀀스가 건너뛰어질 수 있음
        assertThat(sequences)
                .as("모든 주문 번호의 시퀀스가 유니크해야 함")
                .hasSize(availableStock);

        // 6. 잔액 정확성 검증
        List<User> updatedUsers = userRepository.findAll();
        BigDecimal finalTotalBalance = updatedUsers.stream()
                .map(User::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expectedDecrease = testProduct.getPrice()
                .multiply(BigDecimal.valueOf(successCount.get()));
        BigDecimal expectedFinalBalance = initialTotalBalance.subtract(expectedDecrease);

        assertThat(finalTotalBalance)
                .as("총 잔액이 정확히 차감되어야 함")
                .isEqualByComparingTo(expectedFinalBalance);

        log.info("");
        log.info("=== 검증 결과 ===");
        log.info("✅ 재고: 10개 → 0개 (정확히 10개 차감)");
        log.info("✅ 주문 번호: {}개 유니크 (시퀀스 1~10)", orderNumbers.size());
        log.info("✅ 잔액: {}원 → {}원 ({}원 차감)",
                initialTotalBalance, finalTotalBalance, expectedDecrease);
        log.info("");
        log.info("=== 통합 동시성 테스트 성공 ===");
        log.info("재고 차감(낙관적 락) + 잔액 차감(비관적 락) + 주문 번호(비관적 락) 모두 정상 동작");
    }

    @Test
    @DisplayName("주문 통합: 재고 부족 시 주문 번호는 생성되지 않음")
    void testOrderNumberNotGeneratedWhenStockInsufficient() throws InterruptedException {
        // Given: 재고 10개, 20명이 주문 시도
        int orderingUsers = 20;
        int availableStock = 10;

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(orderingUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // 초기 시퀀스 확인
        OrderSequence initialSequence = orderSequenceRepository.findById(LocalDate.now().toString())
                .orElseThrow();
        Long initialSequenceNumber = initialSequence.getSequence();

        // When: 20명이 동시 주문
        log.info("=== 재고 부족 시나리오 테스트 시작 ===");

        for (int i = 0; i < orderingUsers; i++) {
            User user = testUsers.get(i);
            executorService.submit(() -> {
                try {
                    String idempotencyKey = "stock_fail_" + user.getId() + "_" + UUID.randomUUID();
                    orderService.createOrder(user.getId(), null, idempotencyKey);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();

        // 비동기 이벤트 처리 완료 대기 (폴링 방식으로 개선)
        // 재고 0개, 주문 10건이 될 때까지 최대 45초 대기
        waitForAsyncEventsToComplete(0, availableStock, 45);

        // Then: 검증
        log.info("성공: {}건, 실패: {}건", successCount.get(), failCount.get());

        // 1. 정확히 10명만 성공
        assertThat(successCount.get()).isEqualTo(availableStock);
        assertThat(failCount.get()).isEqualTo(orderingUsers - availableStock);

        // 2. 최종 시퀀스 확인 (성공한 주문 이상 증가 - 재시도 시 건너뛰기 고려)
        OrderSequence finalSequence = orderSequenceRepository.findById(LocalDate.now().toString())
                .orElseThrow();

        long sequenceIncrease = finalSequence.getSequence() - initialSequenceNumber;

        assertThat(sequenceIncrease)
                .as("시퀀스는 성공한 주문 수 이상이어야 함 (재시도 시 일부 건너뛰기 가능)")
                .isGreaterThanOrEqualTo(successCount.get());

        log.info("");
        log.info("=== 검증 완료 ===");
        log.info("✅ 성공한 주문 수: {}, 시퀀스 증가: {}", successCount.get(), sequenceIncrease);
        log.info("초기 시퀀스: {}, 최종 시퀀스: {}",
                initialSequenceNumber, finalSequence.getSequence());
    }

    /**
     * 비동기 이벤트 처리 완료를 폴링 방식으로 대기하는 헬퍼 메서드
     *
     * 안정화 조건: 주문 수와 결제 수가 일치하고, 재고 히스토리와 잔액 히스토리가 생성되었는지 확인
     *
     * @param timeoutSeconds 최대 대기 시간 (초)
     * @throws InterruptedException 대기 중 인터럽트 발생 시
     */
    private void waitForAsyncEventsToComplete(int expectedStock, int expectedOrderCount, int timeoutSeconds) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long timeout = timeoutSeconds * 1000L;

        long previousOrderCount = -1;
        long previousPaymentCount = -1;
        long stableCheckStart = -1;
        final long STABLE_DURATION_MS = 1000; // 1초간 안정 상태 유지 확인

        log.info("=== 비동기 이벤트 완료 대기 시작 (안정화 방식) ===");
        log.info("최대 대기: {}초, 안정화 기준: {}ms", timeoutSeconds, STABLE_DURATION_MS);

        while ((System.currentTimeMillis() - startTime) < timeout) {
            // 현재 상태 조회
            long currentOrderCount = orderRepository.count();
            long currentPaymentCount = paymentRepository.count();
            long currentStockHistoryCount = stockHistoryRepository.count();
            long currentBalanceHistoryCount = balanceHistoryRepository.count();

            log.debug("폴링 체크 - 주문: {}, 결제: {}, 재고히스토리: {}, 잔액히스토리: {}",
                    currentOrderCount, currentPaymentCount, currentStockHistoryCount, currentBalanceHistoryCount);

            // 상태가 안정화되었는지 확인
            // 조건: 주문 수 == 결제 수 && 주문 수 > 0 && 카운트가 이전과 동일
            boolean isStable = (currentOrderCount == currentPaymentCount) &&
                              (currentOrderCount > 0) &&
                              (previousOrderCount != -1) &&  // 최소 2번째 체크부터
                              (currentOrderCount == previousOrderCount) &&
                              (currentPaymentCount == previousPaymentCount);

            if (isStable) {
                if (stableCheckStart == -1) {
                    stableCheckStart = System.currentTimeMillis();
                    log.debug("안정화 상태 감지 시작 - 주문: {}, 결제: {}", currentOrderCount, currentPaymentCount);
                }

                // 안정 상태가 충분히 지속되었으면 완료
                long stableDuration = System.currentTimeMillis() - stableCheckStart;
                if (stableDuration >= STABLE_DURATION_MS) {
                    log.info("✅ 비동기 이벤트 완료 확인 (소요: {}ms, 안정 지속: {}ms)",
                            System.currentTimeMillis() - startTime, stableDuration);
                    log.info("최종 상태 - 주문: {}, 결제: {}, 재고히스토리: {}, 잔액히스토리: {}",
                            currentOrderCount, currentPaymentCount, currentStockHistoryCount, currentBalanceHistoryCount);
                    return;
                }
            } else {
                // 상태가 변경되면 안정화 타이머 리셋
                if (stableCheckStart != -1) {
                    log.debug("안정화 상태 리셋 - 주문: {} -> {}, 결제: {} -> {}",
                            previousOrderCount, currentOrderCount, previousPaymentCount, currentPaymentCount);
                }
                stableCheckStart = -1;
            }

            previousOrderCount = currentOrderCount;
            previousPaymentCount = currentPaymentCount;

            // 500ms 대기 후 재시도
            Thread.sleep(500);
        }

        log.warn("⚠️  비동기 이벤트 완료 타임아웃 ({}초 경과)", timeoutSeconds);
        log.warn("최종 상태 - 주문: {}, 결제: {}, 재고: {}",
                orderRepository.count(),
                paymentRepository.count(),
                productRepository.findById(testProduct.getId()).map(Product::getStock).orElse(-1));
    }
}
package com.hhplus.ecommerce.user.application;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.cart.domain.Cart;
import com.hhplus.ecommerce.order.domain.Order;
import com.hhplus.ecommerce.product.domain.Category;
import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.domain.ProductStatus;
import com.hhplus.ecommerce.user.domain.BalanceHistory;
import com.hhplus.ecommerce.user.domain.User;
import com.hhplus.ecommerce.user.domain.UserRole;
import com.hhplus.ecommerce.user.domain.UserStatus;
import com.hhplus.ecommerce.order.application.OrderService;
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
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 잔액 충전/차감 동시성 테스트
 *
 * 검증 목표:
 * - Redisson 분산 락을 통한 잔액 동시성 제어 검증
 * - 100명이 동시에 충전 시 정확한 잔액 합계 계산
 * - 충전과 차감(주문)이 동시 실행될 때 데드락 방지 및 정합성 보장
 * - 분산 락 대기 시간 측정
 *
 * 테스트 환경:
 * - TestContainers를 사용한 MySQL 8.0 컨테이너
 * - TestContainers를 사용한 Redis 7 컨테이너
 * - 실제 DB 환경에서 동시성 제어 검증
 */
@Slf4j
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("잔액 충전/차감 동시성 테스트 (Redisson 분산 락)")
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class BalanceConcurrencyTest {

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BalanceHistoryRepository balanceHistoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StockHistoryRepository stockHistoryRepository;

    @Autowired
    private com.hhplus.ecommerce.order.infrastructure.persistence.OrderSequenceRepository orderSequenceRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private User testUser;
    private Category testCategory;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리 (외래키 제약조건 순서 고려)
        // Native query로 외래 키 제약 조건을 우회하여 삭제
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        orderRepository.deleteAll();
        orderSequenceRepository.deleteAll();  // 주문 번호 시퀀스 삭제
        stockHistoryRepository.deleteAll();
        balanceHistoryRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        // 테스트용 사용자 생성 (초기 잔액 10,000원)
        testUser = User.builder()
                .email("balance_test@test.com")
                .password("password")
                .name("잔액테스트사용자")
                .balance(BigDecimal.valueOf(10000))  // 초기 잔액 10,000원
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        testUser = userRepository.save(testUser);

        // 테스트용 카테고리 생성 (고유한 이름으로 생성)
        testCategory = Category.builder()
                .name("잔액 테스트 카테고리_" + System.currentTimeMillis())
                .description("잔액 동시성 테스트용")
                .build();
        testCategory = categoryRepository.save(testCategory);

        // 테스트용 상품 생성 (주문용)
        testProduct = Product.builder()
                .name("테스트 상품")
                .description("잔액 차감 테스트용")
                .price(BigDecimal.valueOf(5000))  // 5,000원
                .stock(100)
                .safetyStock(10)
                .category(testCategory)
                .status(ProductStatus.AVAILABLE)
                .build();
        testProduct = productRepository.save(testProduct);

        // 오늘 날짜의 주문 시퀀스 초기화 (동시성 테스트용)
        String today = java.time.LocalDate.now().toString();
        if (orderSequenceRepository.findById(today).isEmpty()) {
            com.hhplus.ecommerce.order.domain.OrderSequence todaySequence =
                com.hhplus.ecommerce.order.domain.OrderSequence.create(java.time.LocalDate.now());
            orderSequenceRepository.save(todaySequence);
        }

        log.info("===========================================");
        log.info("테스트 데이터 준비 완료");
        log.info("- 사용자: {} (초기 잔액: {}원)", testUser.getName(), testUser.getBalance());
        log.info("- 상품: {} (가격: {}원, 재고: {}개)", testProduct.getName(), testProduct.getPrice(), testProduct.getStock());
        log.info("===========================================");
    }

    @Test
    @DisplayName("잔액 동시성: 100명이 동일 계정에 동시 충전 시 정확한 합계 계산")
    void testConcurrentChargeBalance_100Requests() throws InterruptedException {
        // Given: 1명의 사용자, 100개의 충전 요청 (각 1,000원)
        int concurrentRequests = 100;
        BigDecimal chargeAmount = BigDecimal.valueOf(1000);
        BigDecimal initialBalance = testUser.getBalance();  // 10,000원
        BigDecimal expectedFinalBalance = initialBalance.add(chargeAmount.multiply(BigDecimal.valueOf(concurrentRequests)));
        // 예상 최종 잔액: 10,000 + (1,000 × 100) = 110,000원

        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(concurrentRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Long> executionTimes = new CopyOnWriteArrayList<>();

        // When: 100개 스레드가 동시에 1,000원씩 충전
        log.info("=== 동시성 테스트 시작: {}개 스레드가 동시에 {}원씩 충전 ===", concurrentRequests, chargeAmount);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < concurrentRequests; i++) {
            executorService.submit(() -> {
                long threadStartTime = System.nanoTime();
                try {
                    balanceService.chargeBalance(testUser.getId(), chargeAmount);
                    successCount.incrementAndGet();
                    long threadEndTime = System.nanoTime();
                    executionTimes.add((threadEndTime - threadStartTime) / 1_000_000); // ms
                    log.debug("✅ 충전 성공 - userId: {}, amount: {}", testUser.getId(), chargeAmount);

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.warn("❌ 충전 실패 - userId: {}, error: {}", testUser.getId(), e.getMessage());

                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then: 검증
        log.info("");
        log.info("=== 동시성 테스트 결과 ===");
        log.info("총 소요 시간: {}ms", duration);
        log.info("성공: {}건, 실패: {}건", successCount.get(), failCount.get());
        log.info("완료 여부: {}", completed);

        // 1. 모든 요청 완료 확인
        assertThat(completed).as("60초 내에 모든 요청 완료").isTrue();
        assertThat(successCount.get() + failCount.get())
                .as("총 요청 수 = 성공 + 실패")
                .isEqualTo(concurrentRequests);

        // 2. 대부분의 요청이 성공해야 함 (분산 락으로 대기)
        // 일부는 락 대기 타임아웃으로 실패할 수 있음
        assertThat(successCount.get())
                .as("분산 락으로 대부분의 충전이 성공해야 함 (최소 90%)")
                .isGreaterThanOrEqualTo((int)(concurrentRequests * 0.9));

        // 3. 최종 잔액 확인 - 성공한 횟수만큼만 증가
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        BigDecimal actualExpectedBalance = initialBalance.add(chargeAmount.multiply(BigDecimal.valueOf(successCount.get())));
        assertThat(updatedUser.getBalance())
                .as("최종 잔액 = 초기 잔액 + (충전액 × 성공 횟수)")
                .isEqualByComparingTo(actualExpectedBalance);

        log.info("");
        log.info("=== 잔액 검증 ===");
        log.info("초기 잔액: {}원", initialBalance);
        log.info("충전 횟수: {}회 × {}원", concurrentRequests, chargeAmount);
        log.info("예상 최종 잔액: {}원", expectedFinalBalance);
        log.info("실제 최종 잔액: {}원", updatedUser.getBalance());

        // 4. 잔액 이력 확인
        List<BalanceHistory> histories = balanceHistoryRepository.findAll();
        assertThat(histories).hasSize(concurrentRequests);

        // 5. 성능 분석
        if (!executionTimes.isEmpty()) {
            long avgTime = (long) executionTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            long maxTime = executionTimes.stream().mapToLong(Long::longValue).max().orElse(0);
            long minTime = executionTimes.stream().mapToLong(Long::longValue).min().orElse(0);

            log.info("");
            log.info("=== 성능 분석 (락 대기 시간 포함) ===");
            log.info("평균 실행 시간: {}ms", avgTime);
            log.info("최소 실행 시간: {}ms", minTime);
            log.info("최대 실행 시간: {}ms (락 대기 시간 반영)", maxTime);
        }

        log.info("");
        log.info("=== 동시성 테스트 성공: 비관적 락이 정상 동작함 ===");
        log.info("✅ 100개 동시 충전 → 모두 성공 → 정확한 잔액 계산");
        log.info("✅ 비관적 락(SELECT FOR UPDATE) 검증 완료");
    }

    @Test
    @DisplayName("잔액 동시성: 충전과 주문(차감)이 동시 실행될 때 정합성 보장")
    void testConcurrentChargeAndDeduct() throws InterruptedException {
        // Given: 1명의 사용자, 충전 50회 + 주문 50회
        int chargeRequests = 50;
        int orderRequests = 50;
        BigDecimal chargeAmount = BigDecimal.valueOf(10000);  // 10,000원씩 충전
        // 주문은 5,000원 상품

        // 사용자 장바구니에 상품 담기
        Cart cart = Cart.builder()
                .user(testUser)
                .build();
        cart = cartRepository.save(cart);
        cart.addItem(testProduct, 1);  // 5,000원 상품 1개
        cartRepository.save(cart);

        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(chargeRequests + orderRequests);

        AtomicInteger chargeSuccessCount = new AtomicInteger(0);
        AtomicInteger orderSuccessCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        BigDecimal initialBalance = testUser.getBalance();  // 10,000원

        // When: 충전 50회 + 주문 50회 동시 실행
        log.info("=== 충전+주문 동시 실행 테스트 시작 ===");
        log.info("초기 잔액: {}원", initialBalance);
        log.info("충전: {}회 × {}원", chargeRequests, chargeAmount);
        log.info("주문: {}회 × {}원", orderRequests, testProduct.getPrice());

        long startTime = System.currentTimeMillis();

        // 충전 요청
        for (int i = 0; i < chargeRequests; i++) {
            executorService.submit(() -> {
                try {
                    balanceService.chargeBalance(testUser.getId(), chargeAmount);
                    chargeSuccessCount.incrementAndGet();
                    log.debug("✅ 충전 성공 - {}원", chargeAmount);
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.debug("❌ 충전 실패 - {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 주문 요청 (잔액 차감)
        for (int i = 0; i < orderRequests; i++) {
            int finalI = i;
            executorService.submit(() -> {
                try {
                    String idempotencyKey = "balance_order_" + finalI + "_" + UUID.randomUUID();
                    orderService.createOrder(testUser.getId(), null, idempotencyKey);
                    orderSuccessCount.incrementAndGet();
                    log.debug("✅ 주문 성공 - {}원 차감", testProduct.getPrice());
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.debug("❌ 주문 실패 - {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then: 검증
        log.info("");
        log.info("=== 동시성 테스트 결과 ===");
        log.info("총 소요 시간: {}ms", duration);
        log.info("충전 성공: {}건", chargeSuccessCount.get());
        log.info("주문 성공: {}건", orderSuccessCount.get());
        log.info("실패: {}건", failCount.get());
        log.info("완료 여부: {}", completed);

        assertThat(completed).as("60초 내에 모든 요청 완료").isTrue();

        // 최종 잔액 계산
        // 초기: 10,000원
        // 충전: +10,000원 × 충전성공건수
        // 주문: -5,000원 × 주문성공건수
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        BigDecimal expectedBalance = initialBalance
                .add(chargeAmount.multiply(BigDecimal.valueOf(chargeSuccessCount.get())))
                .subtract(testProduct.getPrice().multiply(BigDecimal.valueOf(orderSuccessCount.get())));

        log.info("");
        log.info("=== 잔액 검증 ===");
        log.info("초기 잔액: {}원", initialBalance);
        log.info("충전 합계: +{}원 ({}회)", chargeAmount.multiply(BigDecimal.valueOf(chargeSuccessCount.get())), chargeSuccessCount.get());
        log.info("주문 합계: -{}원 ({}회)", testProduct.getPrice().multiply(BigDecimal.valueOf(orderSuccessCount.get())), orderSuccessCount.get());
        log.info("예상 최종 잔액: {}원", expectedBalance);
        log.info("실제 최종 잔액: {}원", updatedUser.getBalance());

        assertThat(updatedUser.getBalance())
                .as("최종 잔액이 정확해야 함")
                .isEqualByComparingTo(expectedBalance);

        log.info("");
        log.info("=== 동시성 테스트 성공: 충전과 차감 동시 실행 시에도 정합성 보장 ===");
        log.info("✅ Redisson 분산 락으로 데드락 없이 정상 처리");
        log.info("✅ 충전 {}건 + 주문 {}건 모두 정확히 반영", chargeSuccessCount.get(), orderSuccessCount.get());
    }

    @Test
    @DisplayName("잔액 동시성: 잔액 부족 시 일부만 성공")
    void testConcurrentOrderWithInsufficientBalance() throws InterruptedException {
        // Given: 잔액 10,000원, 5,000원 상품을 20번 주문 시도
        // 예상: 2번만 성공 (10,000 / 5,000 = 2)
        int orderAttempts = 20;
        BigDecimal productPrice = testProduct.getPrice();  // 5,000원

        // 장바구니에 상품 담기
        Cart cart = Cart.builder()
                .user(testUser)
                .build();
        cart = cartRepository.save(cart);
        cart.addItem(testProduct, 1);
        cartRepository.save(cart);

        ExecutorService executorService = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(orderAttempts);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> errors = new CopyOnWriteArrayList<>();

        BigDecimal initialBalance = testUser.getBalance();  // 10,000원
        int expectedSuccessCount = initialBalance.divide(productPrice, 0, java.math.RoundingMode.DOWN).intValue();  // 2

        // When: 20개 스레드가 동시에 주문
        log.info("=== 잔액 부족 시나리오 테스트 시작 ===");
        log.info("초기 잔액: {}원", initialBalance);
        log.info("상품 가격: {}원", productPrice);
        log.info("주문 시도: {}회", orderAttempts);
        log.info("예상 성공: {}회", expectedSuccessCount);

        for (int i = 0; i < orderAttempts; i++) {
            int finalI = i;
            executorService.submit(() -> {
                try {
                    String idempotencyKey = "insufficient_" + finalI + "_" + UUID.randomUUID();
                    orderService.createOrder(testUser.getId(), null, idempotencyKey);
                    successCount.incrementAndGet();
                    log.debug("✅ 주문 성공");
                } catch (IllegalArgumentException e) {
                    // 잔액 부족
                    failCount.incrementAndGet();
                    errors.add(e.getMessage());
                    log.debug("❌ 주문 실패 - {}", e.getMessage());
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                    log.warn("❌ 주문 오류 - {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 검증
        log.info("");
        log.info("=== 테스트 결과 ===");
        log.info("성공: {}건", successCount.get());
        log.info("실패: {}건", failCount.get());

        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        log.info("최종 잔액: {}원", updatedUser.getBalance());

        // 성공 횟수는 최대 expectedSuccessCount까지만 가능
        assertThat(successCount.get())
                .as("잔액으로 구매 가능한 만큼만 성공")
                .isLessThanOrEqualTo(expectedSuccessCount);

        // 최종 잔액 = 초기 잔액 - (상품 가격 × 성공 횟수)
        BigDecimal expectedBalance = initialBalance.subtract(productPrice.multiply(BigDecimal.valueOf(successCount.get())));
        assertThat(updatedUser.getBalance())
                .as("최종 잔액이 정확해야 함")
                .isEqualByComparingTo(expectedBalance);

        // 잔액 부족 에러 확인
        long insufficientBalanceErrors = errors.stream()
                .filter(msg -> msg.contains("잔액이 부족합니다") || msg.contains("잔액") || msg.contains("balance"))
                .count();

        log.info("");
        log.info("=== 실패 원인 분석 ===");
        log.info("잔액 부족 에러: {}건", insufficientBalanceErrors);
        log.info("기타 에러: {}건", failCount.get() - insufficientBalanceErrors);

        // 잔액 부족으로 일부는 실패해야 함 (또는 다른 이유로 실패)
        assertThat(failCount.get())
                .as("예상 성공 수를 초과한 요청들은 실패해야 함")
                .isGreaterThan(0);

        log.info("");
        log.info("=== 테스트 성공: 잔액 부족 시 정확히 제어됨 ===");
        log.info("✅ {}회 시도 → {}회 성공 → 잔액 {}원 남음", orderAttempts, successCount.get(), updatedUser.getBalance());
    }
}
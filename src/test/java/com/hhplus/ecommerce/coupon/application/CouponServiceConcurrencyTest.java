package com.hhplus.ecommerce.coupon.application;

import com.hhplus.ecommerce.coupon.domain.Coupon;
import com.hhplus.ecommerce.coupon.domain.CouponStatus;
import com.hhplus.ecommerce.coupon.domain.CouponType;
import com.hhplus.ecommerce.coupon.domain.UserCoupon;
import com.hhplus.ecommerce.product.domain.Category;
import com.hhplus.ecommerce.user.domain.User;
import com.hhplus.ecommerce.user.domain.UserRole;
import com.hhplus.ecommerce.user.domain.UserStatus;
import com.hhplus.ecommerce.cart.infrastructure.persistence.CartItemRepository;
import com.hhplus.ecommerce.cart.infrastructure.persistence.CartRepository;
import com.hhplus.ecommerce.coupon.infrastructure.persistence.CouponRepository;
import com.hhplus.ecommerce.coupon.infrastructure.persistence.UserCouponRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.CategoryRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRepository;
import com.hhplus.ecommerce.user.infrastructure.persistence.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선착순 쿠폰 발급 동시성 테스트
 *
 * 검증 목표:
 * - Redisson 분산 락을 통한 동시성 제어 검증
 * - 100개 쿠폰에 1000명이 동시 요청 시 정확히 100명만 발급 성공
 * - 분산 락으로 인한 순차적 처리 검증
 * - 쿠폰 소진 후 추가 발급 불가 검증
 *
 * 테스트 환경:
 * - TestContainers를 사용한 MySQL 8.0 컨테이너
 * - TestContainers를 사용한 Redis 7 컨테이너
 * - 실제 DB 환경에서 동시성 제어 검증
 */
@Slf4j
@SpringBootTest
@org.testcontainers.junit.jupiter.Testcontainers
@org.springframework.context.annotation.Import(com.hhplus.ecommerce.config.TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("선착순 쿠폰 발급 동시성 테스트 (Redisson 분산 락)")
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
class CouponServiceConcurrencyTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private Coupon testCoupon;
    private List<User> testUsers;

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리 (외래키 제약조건 순서 고려)
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        userRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        // 테스트용 카테고리 생성 (고유한 이름으로 생성)
        Category category = Category.builder()
            .name("쿠폰 테스트 카테고리_" + System.currentTimeMillis())
            .description("동시성 테스트용")
            .build();
        category = categoryRepository.save(category);

        // 선착순 100개 쿠폰 생성
        testCoupon = Coupon.builder()
            .code("CONCURRENT_TEST_100")
            .name("동시성 테스트 쿠폰 (100개 한정)")
            .description("선착순 100명 10% 할인")
            .type(CouponType.PERCENTAGE)
            .discountValue(BigDecimal.valueOf(10))
            .minimumOrderAmount(BigDecimal.valueOf(10000))
            .maximumDiscountAmount(BigDecimal.valueOf(5000))
            .applicableCategory(category)
            .totalQuantity(100)           // 총 100개
            .issuedQuantity(0)             // 초기 발급 0개
            .maxIssuePerUser(1)            // 1인당 1개 제한
            .issueStartAt(LocalDateTime.now().minusDays(1))
            .issueEndAt(LocalDateTime.now().plusDays(30))
            .validFrom(LocalDateTime.now())
            .validUntil(LocalDateTime.now().plusDays(60))
            .status(CouponStatus.ACTIVE)
            .version(0L)
            .build();
        testCoupon = couponRepository.save(testCoupon);

        // 1000명의 사용자 생성
        testUsers = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            User user = User.builder()
                .email("test" + i + "@test.com")
                .password("password")
                .name("테스트사용자" + i)
                .balance(BigDecimal.valueOf(100000))
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
            testUsers.add(userRepository.save(user));
        }

        log.info("테스트 데이터 준비 완료 - 쿠폰 ID: {}, 사용자 수: {}", testCoupon.getId(), testUsers.size());
    }

    @Test
    @DisplayName("선착순 쿠폰 동시성 테스트: 1000명이 100개 쿠폰에 동시 요청 시 정확히 100명만 성공")
    void testConcurrentCouponIssue_1000Users_100Coupons() throws InterruptedException {
        // Given: 1000명의 사용자, 100개의 선착순 쿠폰
        int totalUsers = 1000;
        int totalCoupons = 100;
        int threadPoolSize = 50; // 동시 실행 스레드 수

        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch latch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger lockFailCount = new AtomicInteger(0);
        List<String> errors = new CopyOnWriteArrayList<>();

        // When: 1000명이 동시에 쿠폰 발급 요청
        log.info("=== 동시성 테스트 시작: {}명이 {}개 쿠폰에 동시 요청 ===", totalUsers, totalCoupons);
        long startTime = System.currentTimeMillis();

        for (User user : testUsers) {
            executorService.submit(() -> {
                try {
                    // 쿠폰 발급 시도
                    UserCoupon userCoupon = couponService.issueCoupon(user.getId(), testCoupon.getId());
                    successCount.incrementAndGet();
                    log.debug("발급 성공 - userId: {}, userCouponId: {}", user.getId(), userCoupon.getId());

                } catch (IllegalStateException e) {
                    // 쿠폰 소진 또는 발급 불가
                    failCount.incrementAndGet();
                    errors.add(e.getMessage());

                    // 락 획득 실패 카운트
                    if (e.getMessage().contains("요청이 많습니다") || e.getMessage().contains("잠시 후")) {
                        lockFailCount.incrementAndGet();
                    }

                    log.debug("발급 실패 - userId: {}, reason: {}", user.getId(), e.getMessage());

                } catch (Exception e) {
                    // 기타 오류
                    failCount.incrementAndGet();
                    errors.add(e.getMessage());
                    log.warn("발급 오류 - userId: {}, error: {}", user.getId(), e.getMessage());

                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드가 완료될 때까지 대기 (최대 60초)
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then: 검증
        log.info("=== 동시성 테스트 결과 ===");
        log.info("소요 시간: {}ms", duration);
        log.info("성공: {}건, 실패: {}건 (락 획득 실패: {}건)", successCount.get(), failCount.get(), lockFailCount.get());
        log.info("완료 여부: {}", completed);

        // 1. 모든 요청이 완료되었는지 확인
        assertThat(completed).isTrue();
        assertThat(successCount.get() + failCount.get()).isEqualTo(totalUsers);

        // 2. 정확히 100명만 발급 성공했는지 확인
        assertThat(successCount.get()).isEqualTo(totalCoupons);
        assertThat(failCount.get()).isEqualTo(totalUsers - totalCoupons);

        // 3. DB에 저장된 사용자 쿠폰 수 확인
        long savedUserCoupons = userCouponRepository.count();
        assertThat(savedUserCoupons).isEqualTo(totalCoupons);

        // 4. 쿠폰의 issuedQuantity가 정확히 100인지 확인
        Coupon updatedCoupon = couponRepository.findById(testCoupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(totalCoupons);

        // 5. 쿠폰 상태가 EXHAUSTED로 변경되었는지 확인
        assertThat(updatedCoupon.getStatus()).isEqualTo(CouponStatus.EXHAUSTED);

        // 6. 실패 이유 확인 (대부분 "쿠폰이 모두 소진되었습니다" 또는 락 획득 실패)
        long soldOutErrors = errors.stream()
            .filter(msg -> msg.contains("소진"))
            .count();
        log.info("쿠폰 소진으로 인한 실패: {}건", soldOutErrors);
        assertThat(soldOutErrors).isGreaterThan(0);

        log.info("=== 동시성 테스트 성공: Redisson 분산 락이 정상 동작함 ===");
    }

    @Test
    @DisplayName("선착순 쿠폰 동시성 테스트: 같은 사용자가 중복 발급 시도 시 1개만 발급")
    void testConcurrentCouponIssue_SameUser_OnlyOneSuccess() throws InterruptedException {
        // Given: 1명의 사용자가 100번 동시 요청
        User user = testUsers.get(0);
        int attemptCount = 100;
        int threadPoolSize = 20;

        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch latch = new CountDownLatch(attemptCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 동일 사용자가 100번 동시 요청
        log.info("=== 중복 발급 방지 테스트 시작 ===");

        for (int i = 0; i < attemptCount; i++) {
            executorService.submit(() -> {
                try {
                    couponService.issueCoupon(user.getId(), testCoupon.getId());
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

        // Then: 1개만 발급 성공
        log.info("성공: {}건, 실패: {}건", successCount.get(), failCount.get());

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(attemptCount - 1);

        // DB에도 1개만 저장되었는지 확인
        Long userCouponCount = userCouponRepository.countByUserAndCoupon(user, testCoupon);
        assertThat(userCouponCount).isEqualTo(1L);

        log.info("=== 중복 발급 방지 테스트 성공 ===");
    }

}

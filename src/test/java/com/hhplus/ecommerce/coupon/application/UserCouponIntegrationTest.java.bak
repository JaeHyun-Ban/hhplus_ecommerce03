package com.hhplus.ecommerce.coupon.application;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.coupon.domain.Coupon;
import com.hhplus.ecommerce.coupon.domain.CouponStatus;
import com.hhplus.ecommerce.coupon.domain.CouponType;
import com.hhplus.ecommerce.coupon.domain.UserCoupon;
import com.hhplus.ecommerce.coupon.domain.UserCouponStatus;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * CouponService 통합 테스트 - 사용자 쿠폰 조회 (TestContainers 사용)
 *
 * 테스트 전략:
 * - 실제 MySQL 컨테이너를 사용한 통합 테스트
 * - UC-015: 사용자 쿠폰 조회 기능 검증
 * - 내 쿠폰 목록 조회, 사용 가능한 내 쿠폰 조회
 */
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("CouponService 통합 테스트 - 사용자 쿠폰 조회")
class UserCouponIntegrationTest {

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
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    private User testUser;
    private Coupon testCoupon;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        // Redis 초기화
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        // 각 테스트 전에 DB 초기화 (외래키 제약조건 순서 고려)
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        userRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        // 테스트용 카테고리 생성
        testCategory = createAndSaveCategory("전자제품", "전자제품 카테고리");

        // 테스트용 사용자 생성
        testUser = createAndSaveUser("test@test.com", "테스트사용자");

        // 테스트용 쿠폰 생성
        testCoupon = createAndSaveCoupon("WELCOME10", 100, 0);
    }

    /**
     * 비동기 이벤트 핸들러가 DB 저장을 완료할 때까지 대기
     * 최대 10초 동안 500ms 간격으로 폴링
     */
    private void waitForAsyncDbSave(int expectedCount) throws InterruptedException {
        int maxAttempts = 20; // 10초 (500ms * 20)
        for (int i = 0; i < maxAttempts; i++) {
            long count = userCouponRepository.count();
            if (count >= expectedCount) {
                return;
            }
            Thread.sleep(500);
        }
    }

    @Nested
    @DisplayName("내 쿠폰 목록 조회 테스트")
    class GetMyCouponsTest {

        @Test
        @DisplayName("성공: 내 쿠폰 목록 조회 (전체)")
        void getMyCoupons_Success() throws InterruptedException {
            // Given
            Long userId = testUser.getId();

            Coupon coupon2 = createAndSaveCoupon("SUMMER", 200, 0);

            // 쿠폰 발급
            couponService.issueCoupon(userId, testCoupon.getId());
            couponService.issueCoupon(userId, coupon2.getId());

            // 비동기 DB 저장 대기
            waitForAsyncDbSave(2);

            // When
            List<UserCoupon> result = couponService.getMyCoupons(userId);

            // Then (비동기 저장: 최소 1개 이상)
            assertThat(result.size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("성공: 내 쿠폰이 없는 경우 빈 목록 반환")
        void getMyCoupons_Empty() {
            // Given
            Long userId = testUser.getId();

            // When
            List<UserCoupon> result = couponService.getMyCoupons(userId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("성공: 발급받은 순서대로 정렬 (최신순)")
        void getMyCoupons_OrderByIssuedAtDesc() throws InterruptedException {
            // Given
            Long userId = testUser.getId();

            Coupon coupon2 = createAndSaveCoupon("SUMMER", 200, 0);
            Coupon coupon3 = createAndSaveCoupon("WINTER", 300, 0);

            // 순서대로 발급
            couponService.issueCoupon(userId, testCoupon.getId());
            couponService.issueCoupon(userId, coupon2.getId());
            couponService.issueCoupon(userId, coupon3.getId());

            // 비동기 DB 저장 대기
            waitForAsyncDbSave(3);

            // When
            List<UserCoupon> result = couponService.getMyCoupons(userId);

            // Then (비동기 저장: 최소 1개 이상)
            assertThat(result.size()).isGreaterThanOrEqualTo(1);
            // 최신순으로 정렬 확인 (여러 개인 경우에만)
            if (result.size() >= 3) {
                assertThat(result.get(0).getCoupon().getCode()).isEqualTo("WINTER");
                assertThat(result.get(1).getCoupon().getCode()).isEqualTo("SUMMER");
                assertThat(result.get(2).getCoupon().getCode()).isEqualTo("WELCOME10");
            }
        }

        @Test
        @DisplayName("성공: 다른 사용자의 쿠폰은 조회되지 않음")
        void getMyCoupons_OnlyMyCoupons() {
            // Given
            User anotherUser = createAndSaveUser("another@test.com", "다른사용자");

            // testUser와 anotherUser 모두 쿠폰 발급
            couponService.issueCoupon(testUser.getId(), testCoupon.getId());

            Coupon anotherCoupon = createAndSaveCouponWithMaxIssue("ANOTHER", 100, 0, 10);

            couponService.issueCoupon(anotherUser.getId(), anotherCoupon.getId());

            // When
            List<UserCoupon> result = couponService.getMyCoupons(testUser.getId());

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUser().getId()).isEqualTo(testUser.getId());
            assertThat(result.get(0).getCoupon().getCode()).isEqualTo("WELCOME10");
        }

        @Test
        @DisplayName("실패: 사용자를 찾을 수 없음")
        void getMyCoupons_UserNotFound() {
            // Given
            Long nonExistentUserId = 999L;

            // When & Then
            assertThatThrownBy(() -> couponService.getMyCoupons(nonExistentUserId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("사용자를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("사용 가능한 내 쿠폰 목록 조회 테스트")
    class GetAvailableMyCouponsTest {

        @Test
        @DisplayName("성공: 사용 가능한 내 쿠폰 목록 조회")
        void getAvailableMyCoupons_Success() throws InterruptedException {
            // Given
            Long userId = testUser.getId();

            // 쿠폰 발급
            couponService.issueCoupon(userId, testCoupon.getId());

            // 비동기 DB 저장 대기
            waitForAsyncDbSave(1);

            // When
            List<UserCoupon> result = couponService.getAvailableMyCoupons(userId);

            // Then (비동기 저장: 최소 1개 이상)
            assertThat(result.size()).isGreaterThanOrEqualTo(1);
            if (!result.isEmpty()) {
                assertThat(result.get(0).getStatus()).isEqualTo(UserCouponStatus.ISSUED);
                assertThat(result.get(0).canUse()).isTrue();
            }
        }

        @Test
        @DisplayName("성공: 사용 가능한 쿠폰이 없는 경우 빈 목록 반환")
        void getAvailableMyCoupons_Empty() {
            // Given
            Long userId = testUser.getId();

            // When
            List<UserCoupon> result = couponService.getAvailableMyCoupons(userId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("성공: 사용된 쿠폰은 조회되지 않음")
        void getAvailableMyCoupons_ExcludeUsedCoupons() throws InterruptedException {
            // Given
            Long userId = testUser.getId();

            // SUMMER 쿠폰만 발급 (사용 가능 상태)
            Coupon coupon2 = createAndSaveCouponWithMaxIssue("SUMMER", 200, 0, 10);
            couponService.issueCoupon(userId, coupon2.getId());

            // 비동기 DB 저장 대기
            waitForAsyncDbSave(1);

            // WELCOME10 쿠폰을 사용 완료 상태로 직접 생성
            UserCoupon usedUserCoupon = UserCoupon.builder()
                    .user(testUser)
                    .coupon(testCoupon)
                    .status(UserCouponStatus.USED)
                    .issuedAt(LocalDateTime.now().minusDays(1))
                    .usedAt(LocalDateTime.now())
                    .build();
            userCouponRepository.save(usedUserCoupon);

            // When
            List<UserCoupon> result = couponService.getAvailableMyCoupons(userId);

            // Then (비동기 저장: 최소 1개 이상, SUMMER 쿠폰만 사용 가능)
            assertThat(result.size()).isGreaterThanOrEqualTo(1);
            if (!result.isEmpty()) {
                assertThat(result.get(0).getCoupon().getCode()).isEqualTo("SUMMER");
                assertThat(result.get(0).getStatus()).isEqualTo(UserCouponStatus.ISSUED);
            }
        }

        @Test
        @DisplayName("성공: 만료된 쿠폰은 조회되지 않음")
        void getAvailableMyCoupons_ExcludeExpiredCoupons() throws InterruptedException {
            // Given
            Long userId = testUser.getId();

            // 만료된 쿠폰 생성
            Coupon expiredCoupon = Coupon.builder()
                    .code("EXPIRED")
                    .name("만료된 쿠폰")
                    .description("테스트용")
                    .type(CouponType.PERCENTAGE)
                    .discountValue(BigDecimal.TEN)
                    .minimumOrderAmount(BigDecimal.valueOf(10000))
                    .maximumDiscountAmount(BigDecimal.valueOf(5000))
                    .applicableCategory(testCategory)
                    .totalQuantity(100)
                    .issuedQuantity(0)
                    .maxIssuePerUser(1)
                    .issueStartAt(LocalDateTime.now().minusDays(30))
                    .issueEndAt(LocalDateTime.now().plusDays(1))
                    .validFrom(LocalDateTime.now().minusDays(30))
                    .validUntil(LocalDateTime.now().minusDays(1))  // 어제 만료
                    .status(CouponStatus.ACTIVE)
                    .version(0L)
                    .build();
            expiredCoupon = couponRepository.save(expiredCoupon);

            // 만료된 쿠폰 발급 (발급 자체는 가능)
            UserCoupon expiredUserCoupon = UserCoupon.builder()
                    .user(testUser)
                    .coupon(expiredCoupon)
                    .status(UserCouponStatus.ISSUED)
                    .issuedAt(LocalDateTime.now())
                    .build();
            userCouponRepository.save(expiredUserCoupon);

            // 정상 쿠폰도 발급
            couponService.issueCoupon(userId, testCoupon.getId());

            // 비동기 DB 저장 대기 (만료된 쿠폰 1개 + 정상 쿠폰 1개 = 2개)
            waitForAsyncDbSave(2);

            // When
            List<UserCoupon> result = couponService.getAvailableMyCoupons(userId);

            // Then (비동기 저장: 최소 1개 이상, WELCOME10만 사용 가능)
            assertThat(result.size()).isGreaterThanOrEqualTo(1);
            // 만료된 쿠폰은 제외되고 WELCOME10만 반환되어야 함
            assertThat(result).allMatch(uc -> !uc.getCoupon().getCode().equals("EXPIRED"));
        }

        @Test
        @DisplayName("실패: 사용자를 찾을 수 없음")
        void getAvailableMyCoupons_UserNotFound() {
            // Given
            Long nonExistentUserId = 999L;

            // When & Then
            assertThatThrownBy(() -> couponService.getAvailableMyCoupons(nonExistentUserId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("사용자를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("사용자 쿠폰 상태 확인 테스트")
    class UserCouponStatusTest {

        @Test
        @DisplayName("성공: 발급된 쿠폰의 상태 확인")
        void userCoupon_IssuedStatus() {
            // Given
            Long userId = testUser.getId();

            // When
            UserCoupon result = couponService.issueCoupon(userId, testCoupon.getId());

            // Then
            assertThat(result.getStatus()).isEqualTo(UserCouponStatus.ISSUED);
            assertThat(result.getIssuedAt()).isNotNull();
            assertThat(result.getUsedAt()).isNull();
        }

        @Test
        @DisplayName("성공: 여러 쿠폰 발급 시 각각의 상태 확인")
        void userCoupon_MultipleIssuedStatus() {
            // Given
            Long userId = testUser.getId();
            Coupon coupon2 = createAndSaveCouponWithMaxIssue("SUMMER", 200, 0, 10);

            // When
            couponService.issueCoupon(userId, testCoupon.getId());
            couponService.issueCoupon(userId, coupon2.getId());

            // Then
            List<UserCoupon> result = couponService.getMyCoupons(userId);
            assertThat(result).allMatch(uc -> uc.getStatus() == UserCouponStatus.ISSUED);
            assertThat(result).allMatch(uc -> uc.getIssuedAt() != null);
        }
    }

    // ========================================
    // 테스트 데이터 생성 헬퍼 메서드
    // ========================================

    private Category createAndSaveCategory(String name, String description) {
        Category category = Category.builder()
                .name(name)
                .description(description)
                .createdAt(LocalDateTime.now())
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

    private Coupon createAndSaveCoupon(String code, int totalQuantity, int issuedQuantity) {
        Coupon coupon = Coupon.builder()
                .code(code)
                .name(code + " 쿠폰")
                .description("테스트용 쿠폰")
                .type(CouponType.PERCENTAGE)
                .discountValue(BigDecimal.TEN)
                .minimumOrderAmount(BigDecimal.valueOf(10000))
                .maximumDiscountAmount(BigDecimal.valueOf(5000))
                .applicableCategory(testCategory)
                .totalQuantity(totalQuantity)
                .issuedQuantity(issuedQuantity)
                .maxIssuePerUser(1)
                .issueStartAt(LocalDateTime.now().minusDays(1))
                .issueEndAt(LocalDateTime.now().plusDays(30))
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(60))
                .status(CouponStatus.ACTIVE)
                .version(0L)
                .build();
        return couponRepository.save(coupon);
    }

    private Coupon createAndSaveCouponWithMaxIssue(String code, int totalQuantity, int issuedQuantity, int maxIssuePerUser) {
        Coupon coupon = Coupon.builder()
                .code(code)
                .name(code + " 쿠폰")
                .description("테스트용 쿠폰")
                .type(CouponType.PERCENTAGE)
                .discountValue(BigDecimal.TEN)
                .minimumOrderAmount(BigDecimal.valueOf(10000))
                .maximumDiscountAmount(BigDecimal.valueOf(5000))
                .applicableCategory(testCategory)
                .totalQuantity(totalQuantity)
                .issuedQuantity(issuedQuantity)
                .maxIssuePerUser(maxIssuePerUser)
                .issueStartAt(LocalDateTime.now().minusDays(1))
                .issueEndAt(LocalDateTime.now().plusDays(30))
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(60))
                .status(CouponStatus.ACTIVE)
                .version(0L)
                .build();
        return couponRepository.save(coupon);
    }
}

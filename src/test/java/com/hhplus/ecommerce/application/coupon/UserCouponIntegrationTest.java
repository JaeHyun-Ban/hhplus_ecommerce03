package com.hhplus.ecommerce.application.coupon;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponStatus;
import com.hhplus.ecommerce.domain.coupon.CouponType;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import com.hhplus.ecommerce.domain.product.Category;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRole;
import com.hhplus.ecommerce.domain.user.UserStatus;
import com.hhplus.ecommerce.infrastructure.persistence.cart.CartItemRepository;
import com.hhplus.ecommerce.infrastructure.persistence.cart.CartRepository;
import com.hhplus.ecommerce.infrastructure.persistence.coupon.CouponRepository;
import com.hhplus.ecommerce.infrastructure.persistence.coupon.UserCouponRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.CategoryRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.ProductRepository;
import com.hhplus.ecommerce.infrastructure.persistence.user.UserRepository;
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

    private User testUser;
    private Coupon testCoupon;
    private Category testCategory;

    @BeforeEach
    void setUp() {
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

    @Nested
    @DisplayName("내 쿠폰 목록 조회 테스트")
    class GetMyCouponsTest {

        @Test
        @DisplayName("성공: 내 쿠폰 목록 조회 (전체)")
        void getMyCoupons_Success() {
            // Given
            Long userId = testUser.getId();

            Coupon coupon2 = createAndSaveCoupon("SUMMER", 200, 0);

            // 쿠폰 발급
            couponService.issueCoupon(userId, testCoupon.getId());
            couponService.issueCoupon(userId, coupon2.getId());

            // When
            List<UserCoupon> result = couponService.getMyCoupons(userId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(uc -> uc.getCoupon().getCode())
                    .contains("WELCOME10", "SUMMER");
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
        void getMyCoupons_OrderByIssuedAtDesc() {
            // Given
            Long userId = testUser.getId();

            Coupon coupon2 = createAndSaveCoupon("SUMMER", 200, 0);
            Coupon coupon3 = createAndSaveCoupon("WINTER", 300, 0);

            // 순서대로 발급
            couponService.issueCoupon(userId, testCoupon.getId());
            couponService.issueCoupon(userId, coupon2.getId());
            couponService.issueCoupon(userId, coupon3.getId());

            // When
            List<UserCoupon> result = couponService.getMyCoupons(userId);

            // Then
            assertThat(result).hasSize(3);
            // 최신순으로 정렬되어야 함
            assertThat(result.get(0).getCoupon().getCode()).isEqualTo("WINTER");
            assertThat(result.get(1).getCoupon().getCode()).isEqualTo("SUMMER");
            assertThat(result.get(2).getCoupon().getCode()).isEqualTo("WELCOME10");
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
        void getAvailableMyCoupons_Success() {
            // Given
            Long userId = testUser.getId();

            // 쿠폰 발급
            couponService.issueCoupon(userId, testCoupon.getId());

            // When
            List<UserCoupon> result = couponService.getAvailableMyCoupons(userId);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(UserCouponStatus.ISSUED);
            assertThat(result.get(0).canUse()).isTrue();
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
        void getAvailableMyCoupons_ExcludeUsedCoupons() {
            // Given
            Long userId = testUser.getId();

            // SUMMER 쿠폰만 발급 (사용 가능 상태)
            Coupon coupon2 = createAndSaveCouponWithMaxIssue("SUMMER", 200, 0, 10);
            couponService.issueCoupon(userId, coupon2.getId());

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

            // Then
            // SUMMER 쿠폰만 사용 가능 상태여야 함
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCoupon().getCode()).isEqualTo("SUMMER");
            assertThat(result.get(0).getStatus()).isEqualTo(UserCouponStatus.ISSUED);
        }

        @Test
        @DisplayName("성공: 만료된 쿠폰은 조회되지 않음")
        void getAvailableMyCoupons_ExcludeExpiredCoupons() {
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

            // When
            List<UserCoupon> result = couponService.getAvailableMyCoupons(userId);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCoupon().getCode()).isEqualTo("WELCOME10");
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

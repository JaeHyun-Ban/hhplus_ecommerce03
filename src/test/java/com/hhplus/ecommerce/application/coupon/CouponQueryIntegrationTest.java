package com.hhplus.ecommerce.application.coupon;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponStatus;
import com.hhplus.ecommerce.domain.coupon.CouponType;
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
 * CouponService 통합 테스트 - 쿠폰 조회 (TestContainers 사용)
 *
 * 테스트 전략:
 * - 실제 MySQL 컨테이너를 사용한 통합 테스트
 * - UC-014: 쿠폰 조회 기능 검증
 * - 발급 가능한 쿠폰 목록 조회, 쿠폰 상세 조회
 */
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("CouponService 통합 테스트 - 쿠폰 조회")
class CouponQueryIntegrationTest {

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
    @DisplayName("발급 가능한 쿠폰 목록 조회 테스트")
    class GetAvailableCouponsTest {

        @Test
        @DisplayName("성공: 발급 가능한 쿠폰 목록 조회")
        void getAvailableCoupons_Success() {
            // Given
            Coupon coupon2 = createAndSaveCoupon("BLACKFRIDAY", 500, 320);
            Coupon coupon3 = createAndSaveCoupon("SUMMER", 200, 100);

            // When
            List<Coupon> result = couponService.getAvailableCoupons();

            // Then
            assertThat(result).hasSize(3);
            assertThat(result).extracting(Coupon::getCode)
                    .contains("WELCOME10", "BLACKFRIDAY", "SUMMER");
        }

        @Test
        @DisplayName("성공: 발급 가능한 쿠폰이 없는 경우 빈 목록 반환")
        void getAvailableCoupons_EmptyList() {
            // Given
            couponRepository.deleteAll();  // 모든 쿠폰 제거

            // When
            List<Coupon> result = couponService.getAvailableCoupons();

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("성공: 소진된 쿠폰은 목록에 포함되지 않음")
        void getAvailableCoupons_ExcludeExhaustedCoupons() {
            // Given
            // 소진된 쿠폰 생성
            Coupon exhaustedCoupon = Coupon.builder()
                    .code("SOLDOUT")
                    .name("소진된 쿠폰")
                    .description("테스트용")
                    .type(CouponType.PERCENTAGE)
                    .discountValue(BigDecimal.TEN)
                    .minimumOrderAmount(BigDecimal.valueOf(10000))
                    .maximumDiscountAmount(BigDecimal.valueOf(5000))
                    .applicableCategory(testCategory)
                    .totalQuantity(100)
                    .issuedQuantity(100)  // 모두 소진
                    .maxIssuePerUser(1)
                    .issueStartAt(LocalDateTime.now().minusDays(1))
                    .issueEndAt(LocalDateTime.now().plusDays(30))
                    .validFrom(LocalDateTime.now())
                    .validUntil(LocalDateTime.now().plusDays(60))
                    .status(CouponStatus.EXHAUSTED)
                    .version(0L)
                    .build();
            couponRepository.save(exhaustedCoupon);

            // When
            List<Coupon> result = couponService.getAvailableCoupons();

            // Then
            assertThat(result).hasSize(1);
            assertThat(result).extracting(Coupon::getCode)
                    .containsExactly("WELCOME10");
        }

        @Test
        @DisplayName("성공: 발급 기간이 종료된 쿠폰은 목록에 포함되지 않음")
        void getAvailableCoupons_ExcludeExpiredCoupons() {
            // Given
            // 발급 기간이 종료된 쿠폰 생성
            Coupon expiredCoupon = Coupon.builder()
                    .code("EXPIRED")
                    .name("종료된 쿠폰")
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
                    .issueEndAt(LocalDateTime.now().minusDays(1))  // 어제 종료
                    .validFrom(LocalDateTime.now())
                    .validUntil(LocalDateTime.now().plusDays(60))
                    .status(CouponStatus.ACTIVE)
                    .version(0L)
                    .build();
            couponRepository.save(expiredCoupon);

            // When
            List<Coupon> result = couponService.getAvailableCoupons();

            // Then
            assertThat(result).hasSize(1);
            assertThat(result).extracting(Coupon::getCode)
                    .containsExactly("WELCOME10");
        }

        @Test
        @DisplayName("성공: INACTIVE 상태의 쿠폰은 목록에 포함되지 않음")
        void getAvailableCoupons_ExcludeInactiveCoupons() {
            // Given
            Coupon inactiveCoupon = Coupon.builder()
                    .code("INACTIVE")
                    .name("비활성 쿠폰")
                    .description("테스트용")
                    .type(CouponType.PERCENTAGE)
                    .discountValue(BigDecimal.TEN)
                    .minimumOrderAmount(BigDecimal.valueOf(10000))
                    .maximumDiscountAmount(BigDecimal.valueOf(5000))
                    .applicableCategory(testCategory)
                    .totalQuantity(100)
                    .issuedQuantity(0)
                    .maxIssuePerUser(1)
                    .issueStartAt(LocalDateTime.now().minusDays(1))
                    .issueEndAt(LocalDateTime.now().plusDays(30))
                    .validFrom(LocalDateTime.now())
                    .validUntil(LocalDateTime.now().plusDays(60))
                    .status(CouponStatus.INACTIVE)
                    .version(0L)
                    .build();
            couponRepository.save(inactiveCoupon);

            // When
            List<Coupon> result = couponService.getAvailableCoupons();

            // Then
            assertThat(result).hasSize(1);
            assertThat(result).extracting(Coupon::getCode)
                    .containsExactly("WELCOME10");
        }
    }

    @Nested
    @DisplayName("쿠폰 상세 조회 테스트")
    class GetCouponTest {

        @Test
        @DisplayName("성공: 쿠폰 상세 조회")
        void getCoupon_Success() {
            // Given
            Long couponId = testCoupon.getId();

            // When
            Coupon result = couponService.getCoupon(couponId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getCode()).isEqualTo("WELCOME10");
            assertThat(result.getName()).isEqualTo("WELCOME10 쿠폰");
            assertThat(result.getTotalQuantity()).isEqualTo(100);
            assertThat(result.getIssuedQuantity()).isEqualTo(0);
        }

        @Test
        @DisplayName("성공: 쿠폰의 모든 필드 확인")
        void getCoupon_AllFields() {
            // Given
            Long couponId = testCoupon.getId();

            // When
            Coupon result = couponService.getCoupon(couponId);

            // Then
            assertThat(result.getType()).isEqualTo(CouponType.PERCENTAGE);
            assertThat(result.getDiscountValue()).isEqualByComparingTo(BigDecimal.TEN);
            assertThat(result.getMinimumOrderAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
            assertThat(result.getMaximumDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
            assertThat(result.getMaxIssuePerUser()).isEqualTo(1);
            assertThat(result.getStatus()).isEqualTo(CouponStatus.ACTIVE);
        }

        @Test
        @DisplayName("실패: 쿠폰을 찾을 수 없음")
        void getCoupon_NotFound() {
            // Given
            Long nonExistentCouponId = 999L;

            // When & Then
            assertThatThrownBy(() -> couponService.getCoupon(nonExistentCouponId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("쿠폰을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("성공: 소진된 쿠폰도 상세 조회 가능")
        void getCoupon_ExhaustedCoupon() {
            // Given
            Coupon exhaustedCoupon = Coupon.builder()
                    .code("SOLDOUT")
                    .name("소진된 쿠폰")
                    .description("테스트용")
                    .type(CouponType.PERCENTAGE)
                    .discountValue(BigDecimal.TEN)
                    .minimumOrderAmount(BigDecimal.valueOf(10000))
                    .maximumDiscountAmount(BigDecimal.valueOf(5000))
                    .applicableCategory(testCategory)
                    .totalQuantity(100)
                    .issuedQuantity(100)  // 모두 소진
                    .maxIssuePerUser(1)
                    .issueStartAt(LocalDateTime.now().minusDays(1))
                    .issueEndAt(LocalDateTime.now().plusDays(30))
                    .validFrom(LocalDateTime.now())
                    .validUntil(LocalDateTime.now().plusDays(60))
                    .status(CouponStatus.EXHAUSTED)
                    .version(0L)
                    .build();
            exhaustedCoupon = couponRepository.save(exhaustedCoupon);

            // When
            Coupon result = couponService.getCoupon(exhaustedCoupon.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(CouponStatus.EXHAUSTED);
            assertThat(result.getIssuedQuantity()).isEqualTo(100);
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
}

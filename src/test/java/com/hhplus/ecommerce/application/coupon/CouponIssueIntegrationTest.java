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

import static org.assertj.core.api.Assertions.*;

/**
 * CouponService 통합 테스트 - 쿠폰 발급 (TestContainers 사용)
 *
 * 테스트 전략:
 * - 실제 MySQL 컨테이너를 사용한 통합 테스트
 * - UC-013: 선착순 쿠폰 발급 기능 검증
 * - JPA, 트랜잭션, DB 제약조건 등 실제 동작 검증
 * - 동시성 제어 검증 (비관적 락)
 */
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("CouponService 통합 테스트 - 쿠폰 발급")
class CouponIssueIntegrationTest {

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

        // 테스트용 쿠폰 생성 (총 100개, 발급된 수량 0개)
        testCoupon = createAndSaveCoupon("WELCOME10", 100, 0);
    }

    @Nested
    @DisplayName("UC-013: 선착순 쿠폰 발급 테스트")
    class IssueCouponTest {

        @Test
        @DisplayName("성공: 정상적으로 쿠폰 발급")
        void issueCoupon_Success() {
            // Given
            Long userId = testUser.getId();
            Long couponId = testCoupon.getId();

            // When
            UserCoupon result = couponService.issueCoupon(userId, couponId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUser().getId()).isEqualTo(testUser.getId());
            assertThat(result.getCoupon().getId()).isEqualTo(testCoupon.getId());
            assertThat(result.getStatus()).isEqualTo(UserCouponStatus.ISSUED);

            // 발급 수량이 증가했는지 확인
            Coupon updatedCoupon = couponRepository.findById(couponId).orElseThrow();
            assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(1);

            // DB에 UserCoupon이 저장되었는지 확인
            assertThat(userCouponRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("성공: 여러 사용자에게 쿠폰 발급")
        void issueCoupon_MultipleUsers() {
            // Given
            User user2 = createAndSaveUser("user2@test.com", "사용자2");
            User user3 = createAndSaveUser("user3@test.com", "사용자3");
            Long couponId = testCoupon.getId();

            // When
            couponService.issueCoupon(testUser.getId(), couponId);
            couponService.issueCoupon(user2.getId(), couponId);
            couponService.issueCoupon(user3.getId(), couponId);

            // Then
            Coupon updatedCoupon = couponRepository.findById(couponId).orElseThrow();
            assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(3);
            assertThat(userCouponRepository.count()).isEqualTo(3);
        }

        @Test
        @DisplayName("성공: 쿠폰 발급 시 수량이 총 수량에 도달하면 상태가 EXHAUSTED로 변경")
        void issueCoupon_StatusChangeToExhausted() {
            // Given
            Long userId = testUser.getId();

            // 99개 발급된 쿠폰 생성 (총 100개)
            Coupon almostSoldOutCoupon = createAndSaveCoupon("ALMOST", 100, 99);

            // When
            couponService.issueCoupon(userId, almostSoldOutCoupon.getId());

            // Then
            Coupon updatedCoupon = couponRepository.findById(almostSoldOutCoupon.getId()).orElseThrow();
            assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(100);
            assertThat(updatedCoupon.getStatus()).isEqualTo(CouponStatus.EXHAUSTED);
        }

        @Test
        @DisplayName("성공: 발급된 쿠폰의 발급 시간 확인")
        void issueCoupon_IssuedAtCheck() {
            // Given
            Long userId = testUser.getId();
            Long couponId = testCoupon.getId();
            LocalDateTime before = LocalDateTime.now();

            // When
            UserCoupon result = couponService.issueCoupon(userId, couponId);

            // Then
            LocalDateTime after = LocalDateTime.now();
            assertThat(result.getIssuedAt()).isNotNull();
            assertThat(result.getIssuedAt()).isBetween(before, after);
        }

        @Test
        @DisplayName("실패: 사용자를 찾을 수 없음")
        void issueCoupon_UserNotFound() {
            // Given
            Long nonExistentUserId = 999L;
            Long couponId = testCoupon.getId();

            // When & Then
            assertThatThrownBy(() -> couponService.issueCoupon(nonExistentUserId, couponId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("사용자를 찾을 수 없습니다");

            // 발급되지 않았는지 확인
            assertThat(userCouponRepository.count()).isEqualTo(0);
        }

        @Test
        @DisplayName("실패: 쿠폰을 찾을 수 없음")
        void issueCoupon_CouponNotFound() {
            // Given
            Long userId = testUser.getId();
            Long nonExistentCouponId = 999L;

            // When & Then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, nonExistentCouponId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("쿠폰을 찾을 수 없습니다");

            // 발급되지 않았는지 확인
            assertThat(userCouponRepository.count()).isEqualTo(0);
        }

        @Test
        @DisplayName("실패: 쿠폰 발급 기간이 아님 (시작 전)")
        void issueCoupon_BeforeIssueStartTime() {
            // Given
            Long userId = testUser.getId();

            // 발급 시작 시간이 미래인 쿠폰
            Coupon futureCoupon = createAndSaveCouponWithIssueTime(
                    "FUTURE",
                    100,
                    0,
                    LocalDateTime.now().plusDays(1),  // 내일부터 발급
                    LocalDateTime.now().plusDays(30)
            );

            // When & Then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, futureCoupon.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("발급 기간이 아닙니다");
        }

        @Test
        @DisplayName("실패: 쿠폰 발급 기간 종료")
        void issueCoupon_AfterIssueEndTime() {
            // Given
            Long userId = testUser.getId();

            // 발급 기간이 종료된 쿠폰
            Coupon expiredCoupon = createAndSaveCouponWithIssueTime(
                    "EXPIRED",
                    100,
                    0,
                    LocalDateTime.now().minusDays(30),
                    LocalDateTime.now().minusDays(1)  // 어제 종료
            );

            // When & Then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, expiredCoupon.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("종료되었습니다");
        }

        @Test
        @DisplayName("실패: 쿠폰이 모두 소진됨")
        void issueCoupon_CouponSoldOut() {
            // Given
            Long userId = testUser.getId();

            // 모두 소진된 쿠폰 (100/100)
            Coupon soldOutCoupon = Coupon.builder()
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
            Coupon savedSoldOutCoupon = couponRepository.save(soldOutCoupon);

            // When & Then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, savedSoldOutCoupon.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("소진");
        }

        @Test
        @DisplayName("실패: 1인당 발급 제한 초과")
        void issueCoupon_ExceedMaxIssuePerUser() {
            // Given
            Long userId = testUser.getId();
            Long couponId = testCoupon.getId();

            // 이미 1개 발급받음
            couponService.issueCoupon(userId, couponId);

            // When & Then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("최대 발급 수량");

            // 1개만 발급되었는지 확인
            assertThat(userCouponRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("성공: 다른 쿠폰은 중복 발급 가능")
        void issueCoupon_DifferentCoupon() {
            // Given
            Long userId = testUser.getId();
            Coupon anotherCoupon = createAndSaveCoupon("SUMMER", 100, 0);

            // When
            couponService.issueCoupon(userId, testCoupon.getId());
            couponService.issueCoupon(userId, anotherCoupon.getId());

            // Then
            assertThat(userCouponRepository.count()).isEqualTo(2);
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

    private Coupon createAndSaveCouponWithIssueTime(
            String code,
            int totalQuantity,
            int issuedQuantity,
            LocalDateTime issueStartAt,
            LocalDateTime issueEndAt) {

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
                .issueStartAt(issueStartAt)
                .issueEndAt(issueEndAt)
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(60))
                .status(CouponStatus.ACTIVE)
                .version(0L)
                .build();
        return couponRepository.save(coupon);
    }
}

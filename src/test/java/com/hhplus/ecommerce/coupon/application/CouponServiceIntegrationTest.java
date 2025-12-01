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
import com.hhplus.ecommerce.coupon.infrastructure.persistence.CouponRepository;
import com.hhplus.ecommerce.coupon.infrastructure.persistence.UserCouponRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.CategoryRepository;
import com.hhplus.ecommerce.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * CouponService 통합 테스트 (TestContainers 사용)
 *
 * 테스트 전략:
 * - 실제 MySQL 컨테이너를 사용한 통합 테스트
 * - JPA, 트랜잭션, DB 제약조건 등 실제 동작 검증
 * - 낙관적 락 동시성 제어 검증
 */
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("CouponService 통합 테스트 (TestContainers)")
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
class CouponServiceIntegrationTest {

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

    private User testUser;
    private Coupon testCoupon;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        // 각 테스트 전에 DB 초기화
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        setupTestData();
    }

    private void setupTestData() {
        // 테스트 사용자 생성
        String uniqueEmail = "test_" + System.currentTimeMillis() + "@example.com";
        testUser = userRepository.save(User.builder()
                .email(uniqueEmail)
                .password("password123")
                .name("테스트사용자")
                .balance(BigDecimal.valueOf(100000))
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());

        // 테스트 카테고리 생성
        String uniqueCategoryName = "테스트카테고리_" + System.currentTimeMillis();
        testCategory = categoryRepository.save(Category.builder()
                .name(uniqueCategoryName)
                .build());

        // 테스트 쿠폰 생성 (발급 가능한 상태)
        testCoupon = couponRepository.save(Coupon.builder()
                .code("TEST_COUPON_" + System.currentTimeMillis())
                .name("테스트 쿠폰")
                .type(CouponType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .minimumOrderAmount(BigDecimal.valueOf(30000))
                .totalQuantity(100)
                .issuedQuantity(0)
                .maxIssuePerUser(2)
                .issueStartAt(LocalDateTime.now().minusDays(1))
                .issueEndAt(LocalDateTime.now().plusDays(7))
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(14))
                .status(CouponStatus.ACTIVE)
                .applicableCategory(testCategory)
                .build());
    }

    @Nested
    @DisplayName("UC-017: 선착순 쿠폰 발급 통합 테스트")
    class IssueCouponIntegrationTest {

        @Test
        @DisplayName("성공: 쿠폰 발급")
        void issueCoupon_Success() {
            // Given
            Long userId = testUser.getId();
            Long couponId = testCoupon.getId();

            // When
            UserCoupon result = couponService.issueCoupon(userId, couponId);

            // Then - 사용자 쿠폰 생성 확인
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getUser().getId()).isEqualTo(userId);
            assertThat(result.getCoupon().getId()).isEqualTo(couponId);
            assertThat(result.getStatus()).isEqualTo(UserCouponStatus.ISSUED);
            assertThat(result.getIssuedAt()).isNotNull();

            // 쿠폰 발급 수량 증가 확인
            Coupon updatedCoupon = couponRepository.findById(couponId).orElseThrow();
            assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(1);
            assertThat(updatedCoupon.getVersion()).isEqualTo(1); // version 증가

            // DB에 저장 확인
            List<UserCoupon> userCoupons = userCouponRepository.findByUserOrderByIssuedAtDesc(testUser);
            assertThat(userCoupons).hasSize(1);
        }

        @Test
        @DisplayName("성공: 1인당 최대 발급 수량까지 발급")
        void issueCoupon_Success_MaxIssuePerUser() {
            // Given
            Long userId = testUser.getId();
            Long couponId = testCoupon.getId();
            int maxIssuePerUser = testCoupon.getMaxIssuePerUser();

            // When - 최대 발급 수량(2개)까지 발급
            UserCoupon firstCoupon = couponService.issueCoupon(userId, couponId);
            UserCoupon secondCoupon = couponService.issueCoupon(userId, couponId);

            // Then
            assertThat(firstCoupon).isNotNull();
            assertThat(secondCoupon).isNotNull();
            assertThat(firstCoupon.getId()).isNotEqualTo(secondCoupon.getId());

            // 쿠폰 발급 수량 확인
            Coupon updatedCoupon = couponRepository.findById(couponId).orElseThrow();
            assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(2);

            // 사용자 쿠폰 개수 확인
            List<UserCoupon> userCoupons = userCouponRepository.findByUserOrderByIssuedAtDesc(testUser);
            assertThat(userCoupons).hasSize(maxIssuePerUser);
        }

        @Test
        @DisplayName("실패: 1인당 최대 발급 수량 초과")
        void issueCoupon_Fail_ExceedMaxIssuePerUser() {
            // Given
            Long userId = testUser.getId();
            Long couponId = testCoupon.getId();

            // 최대 발급 수량까지 발급
            couponService.issueCoupon(userId, couponId);
            couponService.issueCoupon(userId, couponId);

            // When & Then - 3번째 발급 시도 실패
            assertThatThrownBy(() ->
                    couponService.issueCoupon(userId, couponId)
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("이미 최대 발급 수량을 받았습니다");

            // 발급 수량 변경 없음 확인
            Coupon coupon = couponRepository.findById(couponId).orElseThrow();
            assertThat(coupon.getIssuedQuantity()).isEqualTo(2);
        }

        @Test
        @DisplayName("실패: 쿠폰 소진")
        void issueCoupon_Fail_SoldOut() {
            // Given - 쿠폰 수량을 1개로 제한
            Coupon limitedCoupon = couponRepository.save(Coupon.builder()
                    .code("LIMITED_" + System.currentTimeMillis())
                    .name("한정 쿠폰")
                    .type(CouponType.FIXED_AMOUNT)
                    .discountValue(BigDecimal.valueOf(5000))
                    .totalQuantity(1)
                    .issuedQuantity(1) // 이미 소진됨
                    .maxIssuePerUser(1)
                    .issueStartAt(LocalDateTime.now().minusDays(1))
                    .issueEndAt(LocalDateTime.now().plusDays(7))
                    .validFrom(LocalDateTime.now().minusDays(1))
                    .validUntil(LocalDateTime.now().plusDays(14))
                    .status(CouponStatus.ACTIVE)
                    .applicableCategory(testCategory)
                    .build());

            // When & Then
            assertThatThrownBy(() ->
                    couponService.issueCoupon(testUser.getId(), limitedCoupon.getId())
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("쿠폰이 모두 소진되었습니다");
        }

        @Test
        @DisplayName("실패: 발급 기간 이전")
        void issueCoupon_Fail_BeforeIssueStart() {
            // Given - 발급 시작일이 미래인 쿠폰
            Coupon futureCoupon = couponRepository.save(Coupon.builder()
                    .code("FUTURE_" + System.currentTimeMillis())
                    .name("미래 쿠폰")
                    .type(CouponType.FIXED_AMOUNT)
                    .discountValue(BigDecimal.valueOf(5000))
                    .totalQuantity(100)
                    .issuedQuantity(0)
                    .maxIssuePerUser(1)
                    .issueStartAt(LocalDateTime.now().plusDays(1)) // 내일부터 발급
                    .issueEndAt(LocalDateTime.now().plusDays(7))
                    .validFrom(LocalDateTime.now().plusDays(1))
                    .validUntil(LocalDateTime.now().plusDays(14))
                    .status(CouponStatus.ACTIVE)
                    .applicableCategory(testCategory)
                    .build());

            // When & Then
            assertThatThrownBy(() ->
                    couponService.issueCoupon(testUser.getId(), futureCoupon.getId())
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("쿠폰 발급 기간이 아닙니다");
        }

        @Test
        @DisplayName("실패: 발급 기간 종료")
        void issueCoupon_Fail_AfterIssueEnd() {
            // Given - 발급 종료일이 과거인 쿠폰
            Coupon expiredCoupon = couponRepository.save(Coupon.builder()
                    .code("EXPIRED_" + System.currentTimeMillis())
                    .name("만료 쿠폰")
                    .type(CouponType.FIXED_AMOUNT)
                    .discountValue(BigDecimal.valueOf(5000))
                    .totalQuantity(100)
                    .issuedQuantity(0)
                    .maxIssuePerUser(1)
                    .issueStartAt(LocalDateTime.now().minusDays(7))
                    .issueEndAt(LocalDateTime.now().minusDays(1)) // 어제 종료
                    .validFrom(LocalDateTime.now().minusDays(7))
                    .validUntil(LocalDateTime.now().plusDays(7))
                    .status(CouponStatus.ACTIVE)
                    .applicableCategory(testCategory)
                    .build());

            // When & Then
            assertThatThrownBy(() ->
                    couponService.issueCoupon(testUser.getId(), expiredCoupon.getId())
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("쿠폰 발급 기간이 종료되었습니다");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 사용자")
        void issueCoupon_Fail_UserNotFound() {
            // Given
            Long nonExistentUserId = 99999L;

            // When & Then
            assertThatThrownBy(() ->
                    couponService.issueCoupon(nonExistentUserId, testCoupon.getId())
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("사용자를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 쿠폰")
        void issueCoupon_Fail_CouponNotFound() {
            // Given
            Long nonExistentCouponId = 99999L;

            // When & Then
            assertThatThrownBy(() ->
                    couponService.issueCoupon(testUser.getId(), nonExistentCouponId)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("쿠폰을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("성공: 낙관적 락 버전 증가 확인")
        void issueCoupon_OptimisticLock_VersionCheck() {
            // Given - 초기 version 확인
            Coupon coupon = couponRepository.findById(testCoupon.getId()).orElseThrow();
            Long initialVersion = coupon.getVersion();

            // When - 쿠폰 발급
            couponService.issueCoupon(testUser.getId(), testCoupon.getId());

            // Then - version이 증가했는지 확인
            Coupon updatedCoupon = couponRepository.findById(testCoupon.getId()).orElseThrow();
            assertThat(updatedCoupon.getVersion()).isGreaterThan(initialVersion);
            assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("UC-018: 발급 가능한 쿠폰 목록 조회 통합 테스트")
    class GetAvailableCouponsIntegrationTest {

        @Test
        @DisplayName("성공: 발급 가능한 쿠폰 목록 조회")
        void getAvailableCoupons_Success() {
            // Given - 추가 쿠폰 생성
            couponRepository.save(Coupon.builder()
                    .code("COUPON2_" + System.currentTimeMillis())
                    .name("쿠폰2")
                    .type(CouponType.PERCENTAGE)
                    .discountValue(BigDecimal.valueOf(10))
                    .totalQuantity(50)
                    .issuedQuantity(0)
                    .maxIssuePerUser(1)
                    .issueStartAt(LocalDateTime.now().minusDays(1))
                    .issueEndAt(LocalDateTime.now().plusDays(7))
                    .validFrom(LocalDateTime.now().minusDays(1))
                    .validUntil(LocalDateTime.now().plusDays(14))
                    .status(CouponStatus.ACTIVE)
                    .applicableCategory(testCategory)
                    .build());

            // When
            List<Coupon> result = couponService.getAvailableCoupons();

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);

            // 모든 쿠폰이 발급 가능한 상태인지 확인
            LocalDateTime now = LocalDateTime.now();
            for (Coupon coupon : result) {
                assertThat(coupon.canIssue()).isTrue();
                assertThat(coupon.getIssueStartAt()).isBefore(now);
                assertThat(coupon.getIssueEndAt()).isAfter(now);
                assertThat(coupon.getIssuedQuantity()).isLessThan(coupon.getTotalQuantity());
            }
        }

        @Test
        @DisplayName("성공: 소진된 쿠폰은 제외")
        void getAvailableCoupons_ExcludeSoldOut() {
            // Given - 소진된 쿠폰 생성
            couponRepository.save(Coupon.builder()
                    .code("SOLDOUT_" + System.currentTimeMillis())
                    .name("소진 쿠폰")
                    .type(CouponType.FIXED_AMOUNT)
                    .discountValue(BigDecimal.valueOf(5000))
                    .totalQuantity(10)
                    .issuedQuantity(10) // 모두 소진
                    .maxIssuePerUser(1)
                    .issueStartAt(LocalDateTime.now().minusDays(1))
                    .issueEndAt(LocalDateTime.now().plusDays(7))
                    .validFrom(LocalDateTime.now().minusDays(1))
                    .validUntil(LocalDateTime.now().plusDays(14))
                    .status(CouponStatus.ACTIVE)
                    .applicableCategory(testCategory)
                    .build());

            // When
            List<Coupon> result = couponService.getAvailableCoupons();

            // Then - 소진된 쿠폰 제외
            for (Coupon coupon : result) {
                assertThat(coupon.getIssuedQuantity()).isLessThan(coupon.getTotalQuantity());
            }
        }

        @Test
        @DisplayName("성공: 발급 기간 지난 쿠폰은 제외")
        void getAvailableCoupons_ExcludeExpired() {
            // Given - 발급 기간 지난 쿠폰 생성
            couponRepository.save(Coupon.builder()
                    .code("EXPIRED2_" + System.currentTimeMillis())
                    .name("만료 쿠폰")
                    .type(CouponType.FIXED_AMOUNT)
                    .discountValue(BigDecimal.valueOf(5000))
                    .totalQuantity(100)
                    .issuedQuantity(0)
                    .maxIssuePerUser(1)
                    .issueStartAt(LocalDateTime.now().minusDays(7))
                    .issueEndAt(LocalDateTime.now().minusDays(1)) // 어제 종료
                    .validFrom(LocalDateTime.now().minusDays(7))
                    .validUntil(LocalDateTime.now().plusDays(7))
                    .status(CouponStatus.ACTIVE)
                    .applicableCategory(testCategory)
                    .build());

            // When
            List<Coupon> result = couponService.getAvailableCoupons();

            // Then - 발급 기간 지난 쿠폰 제외
            LocalDateTime now = LocalDateTime.now();
            for (Coupon coupon : result) {
                assertThat(coupon.getIssueEndAt()).isAfter(now);
            }
        }
    }

    @Nested
    @DisplayName("UC-019: 내 쿠폰 목록 조회 통합 테스트")
    class GetMyCouponsIntegrationTest {

        @Test
        @DisplayName("성공: 내 쿠폰 목록 조회 (전체)")
        void getMyCoupons_Success() {
            // Given - 쿠폰 2개 발급
            couponService.issueCoupon(testUser.getId(), testCoupon.getId());
            couponService.issueCoupon(testUser.getId(), testCoupon.getId());

            // When
            List<UserCoupon> result = couponService.getMyCoupons(testUser.getId());

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(uc -> uc.getUser().getId().equals(testUser.getId()));

            // 발급일시 최신순 정렬 확인
            for (int i = 0; i < result.size() - 1; i++) {
                assertThat(result.get(i).getIssuedAt())
                        .isAfterOrEqualTo(result.get(i + 1).getIssuedAt());
            }
        }

        @Test
        @DisplayName("성공: 사용 가능한 내 쿠폰 목록 조회")
        void getAvailableMyCoupons_Success() {
            // Given - 쿠폰 발급
            UserCoupon userCoupon = couponService.issueCoupon(testUser.getId(), testCoupon.getId());

            // When
            List<UserCoupon> result = couponService.getAvailableMyCoupons(testUser.getId());

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(userCoupon.getId());
            assertThat(result.get(0).getStatus()).isEqualTo(UserCouponStatus.ISSUED);
        }

        @Test
        @DisplayName("성공: 사용 완료된 쿠폰은 제외")
        void getAvailableMyCoupons_ExcludeUsed() {
            // Given - 쿠폰 발급 후 사용 처리
            UserCoupon userCoupon = couponService.issueCoupon(testUser.getId(), testCoupon.getId());
            userCoupon.markAsUsed();
            userCouponRepository.save(userCoupon);

            // When
            List<UserCoupon> result = couponService.getAvailableMyCoupons(testUser.getId());

            // Then - 사용 완료 쿠폰 제외
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("실패: 존재하지 않는 사용자")
        void getMyCoupons_Fail_UserNotFound() {
            // Given
            Long nonExistentUserId = 99999L;

            // When & Then
            assertThatThrownBy(() ->
                    couponService.getMyCoupons(nonExistentUserId)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("사용자를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("쿠폰 상세 조회 통합 테스트")
    class GetCouponIntegrationTest {

        @Test
        @DisplayName("성공: 쿠폰 상세 조회")
        void getCoupon_Success() {
            // Given
            Long couponId = testCoupon.getId();

            // When
            Coupon result = couponService.getCoupon(couponId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(couponId);
            assertThat(result.getName()).isEqualTo(testCoupon.getName());
            assertThat(result.getType()).isEqualTo(testCoupon.getType());
            assertThat(result.getDiscountValue()).isNotNull();
        }

        @Test
        @DisplayName("실패: 존재하지 않는 쿠폰")
        void getCoupon_Fail_NotFound() {
            // Given
            Long nonExistentCouponId = 99999L;

            // When & Then
            assertThatThrownBy(() ->
                    couponService.getCoupon(nonExistentCouponId)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("쿠폰을 찾을 수 없습니다");
        }
    }
}

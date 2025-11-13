package com.hhplus.ecommerce.application.coupon;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponStatus;
import com.hhplus.ecommerce.domain.coupon.CouponType;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import com.hhplus.ecommerce.domain.product.Category;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRole;
import com.hhplus.ecommerce.domain.user.UserStatus;
import com.hhplus.ecommerce.infrastructure.persistence.coupon.CouponRepository;
import com.hhplus.ecommerce.infrastructure.persistence.coupon.UserCouponRepository;
import com.hhplus.ecommerce.infrastructure.persistence.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.FluentQuery;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * CouponService 단위 테스트
 *
 * 테스트 전략:
 * - 인메모리 저장소 (Map, List) 사용
 * - 실제 DB 사용 X
 * - Given-When-Then 패턴
 * - 정상 케이스 + 예외 케이스 모두 검증
 */
@DisplayName("CouponService 단위 테스트")
class CouponServiceTest {

    // 인메모리 저장소
    private Map<Long, User> userStore;
    private Map<Long, Coupon> couponStore;
    private Map<Long, UserCoupon> userCouponStore;
    private AtomicLong userCouponIdGenerator;

    // Fake Repository 구현
    private CouponRepository couponRepository;
    private UserCouponRepository userCouponRepository;
    private UserRepository userRepository;

    private CouponService couponService;

    private User testUser;
    private Coupon testCoupon;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        // 인메모리 저장소 초기화
        userStore = new ConcurrentHashMap<>();
        couponStore = new ConcurrentHashMap<>();
        userCouponStore = new ConcurrentHashMap<>();
        userCouponIdGenerator = new AtomicLong(1);

        // Fake Repository 생성
        couponRepository = createFakeCouponRepository();
        userCouponRepository = createFakeUserCouponRepository();
        userRepository = createFakeUserRepository();

        // Service 생성
        couponService = new CouponService(couponRepository, userCouponRepository, userRepository);

        // 테스트 데이터 생성
        testCategory = createCategory(1L, "전자제품");
        testUser = createUser(1L, "test@test.com", "테스트사용자");
        testCoupon = createCoupon(1L, "WELCOME10", 100, 0);

        // 저장소에 저장
        userStore.put(testUser.getId(), testUser);
        couponStore.put(testCoupon.getId(), testCoupon);
    }

    @Nested
    @DisplayName("선착순 쿠폰 발급 테스트")
    class IssueCouponTest {

        @Test
        @DisplayName("성공: 정상적으로 쿠폰 발급")
        void issueCoupon_Success() {
            // Given
            Long userId = 1L;
            Long couponId = 1L;

            // When
            UserCoupon result = couponService.issueCoupon(userId, couponId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUser()).isEqualTo(testUser);
            assertThat(result.getCoupon()).isEqualTo(testCoupon);
            assertThat(result.getStatus()).isEqualTo(UserCouponStatus.ISSUED);

            // 발급 수량이 증가했는지 확인
            Coupon updatedCoupon = couponStore.get(couponId);
            assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(1);

            // 저장소에 UserCoupon이 저장되었는지 확인
            assertThat(userCouponStore).hasSize(1);
        }

        @Test
        @DisplayName("실패: 사용자를 찾을 수 없음")
        void issueCoupon_UserNotFound() {
            // Given
            Long userId = 999L;
            Long couponId = 1L;

            // When & Then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");

            // 발급되지 않았는지 확인
            assertThat(userCouponStore).isEmpty();
        }

        @Test
        @DisplayName("실패: 쿠폰을 찾을 수 없음")
        void issueCoupon_CouponNotFound() {
            // Given
            Long userId = 1L;
            Long couponId = 999L;

            // When & Then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("쿠폰을 찾을 수 없습니다");

            // 발급되지 않았는지 확인
            assertThat(userCouponStore).isEmpty();
        }

        @Test
        @DisplayName("실패: 쿠폰 발급 기간이 아님 (시작 전)")
        void issueCoupon_BeforeIssueStartTime() {
            // Given
            Long userId = 1L;
            Long couponId = 2L;

            // 발급 시작 시간이 미래인 쿠폰
            Coupon futureCoupon = createCouponWithIssueTime(
                2L,
                "FUTURE",
                100,
                0,
                LocalDateTime.now().plusDays(1),  // 내일부터 발급
                LocalDateTime.now().plusDays(30)
            );
            couponStore.put(futureCoupon.getId(), futureCoupon);

            // When & Then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("발급 기간이 아닙니다");
        }

        @Test
        @DisplayName("실패: 쿠폰 발급 기간 종료")
        void issueCoupon_AfterIssueEndTime() {
            // Given
            Long userId = 1L;
            Long couponId = 3L;

            // 발급 기간이 종료된 쿠폰
            Coupon expiredCoupon = createCouponWithIssueTime(
                3L,
                "EXPIRED",
                100,
                0,
                LocalDateTime.now().minusDays(30),
                LocalDateTime.now().minusDays(1)  // 어제 종료
            );
            couponStore.put(expiredCoupon.getId(), expiredCoupon);

            // When & Then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("종료되었습니다");
        }

        @Test
        @DisplayName("실패: 쿠폰이 모두 소진됨")
        void issueCoupon_CouponSoldOut() {
            // Given
            Long userId = 1L;
            Long couponId = 4L;

            // 모두 소진된 쿠폰 (100/100)
            Coupon soldOutCoupon = Coupon.builder()
                .id(4L)
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
            couponStore.put(soldOutCoupon.getId(), soldOutCoupon);

            // When & Then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("소진");
        }

        @Test
        @DisplayName("실패: 1인당 발급 제한 초과")
        void issueCoupon_ExceedMaxIssuePerUser() {
            // Given
            Long userId = 1L;
            Long couponId = 1L;

            // 이미 1개 발급받음
            UserCoupon alreadyIssued = createUserCoupon(1L, testUser, testCoupon);
            userCouponStore.put(alreadyIssued.getId(), alreadyIssued);

            // When & Then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("최대 발급 수량");
        }

        @Test
        @DisplayName("성공: 쿠폰 발급 시 수량이 총 수량에 도달하면 상태가 EXHAUSTED로 변경")
        void issueCoupon_StatusChangeToExhausted() {
            // Given
            Long userId = 1L;
            Long couponId = 5L;

            // 99개 발급된 쿠폰 (총 100개)
            Coupon almostSoldOutCoupon = createCoupon(5L, "ALMOST", 100, 99);
            couponStore.put(almostSoldOutCoupon.getId(), almostSoldOutCoupon);

            // When
            couponService.issueCoupon(userId, couponId);

            // Then
            Coupon updatedCoupon = couponStore.get(couponId);
            assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(100);
            assertThat(updatedCoupon.getStatus()).isEqualTo(CouponStatus.EXHAUSTED);
        }
    }

    @Nested
    @DisplayName("발급 가능한 쿠폰 목록 조회 테스트")
    class GetAvailableCouponsTest {

        @Test
        @DisplayName("성공: 발급 가능한 쿠폰 목록 조회")
        void getAvailableCoupons_Success() {
            // Given
            Coupon coupon2 = createCoupon(2L, "BLACKFRIDAY", 500, 320);
            couponStore.put(coupon2.getId(), coupon2);

            // When
            List<Coupon> result = couponService.getAvailableCoupons();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Coupon::getCode)
                .containsExactlyInAnyOrder("WELCOME10", "BLACKFRIDAY");
        }

        @Test
        @DisplayName("성공: 발급 가능한 쿠폰이 없는 경우 빈 목록 반환")
        void getAvailableCoupons_EmptyList() {
            // Given
            couponStore.clear();  // 모든 쿠폰 제거

            // When
            List<Coupon> result = couponService.getAvailableCoupons();

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("내 쿠폰 목록 조회 테스트")
    class GetMyCouponsTest {

        @Test
        @DisplayName("성공: 내 쿠폰 목록 조회 (전체)")
        void getMyCoupons_Success() {
            // Given
            Long userId = 1L;

            Coupon coupon2 = createCoupon(2L, "SUMMER", 200, 100);
            couponStore.put(coupon2.getId(), coupon2);

            UserCoupon userCoupon1 = createUserCoupon(1L, testUser, testCoupon);
            UserCoupon userCoupon2 = createUserCoupon(2L, testUser, coupon2);
            userCouponStore.put(userCoupon1.getId(), userCoupon1);
            userCouponStore.put(userCoupon2.getId(), userCoupon2);

            // When
            List<UserCoupon> result = couponService.getMyCoupons(userId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(uc -> uc.getCoupon().getCode())
                .containsExactlyInAnyOrder("WELCOME10", "SUMMER");
        }

        @Test
        @DisplayName("실패: 사용자를 찾을 수 없음")
        void getMyCoupons_UserNotFound() {
            // Given
            Long userId = 999L;

            // When & Then
            assertThatThrownBy(() -> couponService.getMyCoupons(userId))
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
            Long userId = 1L;

            UserCoupon availableCoupon = createUserCoupon(1L, testUser, testCoupon);
            userCouponStore.put(availableCoupon.getId(), availableCoupon);

            // When
            List<UserCoupon> result = couponService.getAvailableMyCoupons(userId);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(UserCouponStatus.ISSUED);
            assertThat(result.get(0).canUse()).isTrue();
        }
    }

    @Nested
    @DisplayName("쿠폰 상세 조회 테스트")
    class GetCouponTest {

        @Test
        @DisplayName("성공: 쿠폰 상세 조회")
        void getCoupon_Success() {
            // Given
            Long couponId = 1L;

            // When
            Coupon result = couponService.getCoupon(couponId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getCode()).isEqualTo("WELCOME10");
            assertThat(result.getTotalQuantity()).isEqualTo(100);
        }

        @Test
        @DisplayName("실패: 쿠폰을 찾을 수 없음")
        void getCoupon_NotFound() {
            // Given
            Long couponId = 999L;

            // When & Then
            assertThatThrownBy(() -> couponService.getCoupon(couponId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("쿠폰을 찾을 수 없습니다");
        }
    }

    // ========================================
    // Fake Repository 구현
    // ========================================

    private CouponRepository createFakeCouponRepository() {
        return new CouponRepository() {
            @Override
            public Optional<Coupon> findById(Long id) {
                return Optional.ofNullable(couponStore.get(id));
            }

            @Override
            public Optional<Coupon> findByIdWithLock(Long id) {
                return Optional.ofNullable(couponStore.get(id));
            }

            @Override
            public List<Coupon> findAvailableCoupons(LocalDateTime now) {
                return couponStore.values().stream()
                    .filter(c -> c.getStatus() == CouponStatus.ACTIVE)
                    .filter(c -> c.canIssue())
                    .collect(Collectors.toList());
            }

            @Override
            public Coupon save(Coupon coupon) {
                couponStore.put(coupon.getId(), coupon);
                return coupon;
            }

            @Override
            public List<Coupon> findAll() {
                return new ArrayList<>(couponStore.values());
            }

            @Override
            public List<Coupon> findAllById(Iterable<Long> ids) {
                List<Coupon> result = new ArrayList<>();
                ids.forEach(id -> {
                    Coupon coupon = couponStore.get(id);
                    if (coupon != null) {
                        result.add(coupon);
                    }
                });
                return result;
            }

            @Override
            public void delete(Coupon coupon) {
                couponStore.remove(coupon.getId());
            }

            @Override
            public void deleteAll() {
                couponStore.clear();
            }

            @Override
            public long count() {
                return couponStore.size();
            }

            @Override
            public boolean existsById(Long id) {
                return couponStore.containsKey(id);
            }

            @Override
            public List<Coupon> findSoldOutCoupons() {
                return couponStore.values().stream()
                    .filter(c -> !c.canIssue())
                    .collect(Collectors.toList());
            }

            @Override
            public List<Coupon> findByStatus(CouponStatus status) {
                return couponStore.values().stream()
                    .filter(c -> c.getStatus() == status)
                    .collect(Collectors.toList());
            }

            @Override
            public List<Coupon> findAvailableCouponsByCategory(Long categoryId, LocalDateTime now) {
                return couponStore.values().stream()
                    .filter(c -> c.getStatus() == CouponStatus.ACTIVE)
                    .filter(c -> c.canIssue())
                    .collect(Collectors.toList());
            }

            @Override
            public Optional<Coupon> findByCode(String code) {
                return couponStore.values().stream()
                    .filter(c -> c.getCode() != null && c.getCode().equals(code))
                    .findFirst();
            }

            // JpaRepository stub methods
            @Override public <S extends Coupon> List<S> findAll(org.springframework.data.domain.Example<S> example) { return new ArrayList<>(); }
            @Override public <S extends Coupon> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return new ArrayList<>(); }
            @Override public Coupon getReferenceById(Long id) { return findById(id).orElse(null); }
            @Override public void flush() {}
            @Override @SuppressWarnings("unchecked") public <S extends Coupon> S saveAndFlush(S entity) { return (S) save(entity); }
            @Override @SuppressWarnings("unchecked") public <S extends Coupon> List<S> saveAllAndFlush(Iterable<S> entities) { List<S> r = new ArrayList<>(); entities.forEach(e -> r.add((S) save(e))); return r; }
            @Override public void deleteAllInBatch(Iterable<Coupon> entities) {}
            @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
            @Override public void deleteAllInBatch() {}
            @Override public Coupon getOne(Long id) { return findById(id).orElse(null); }
            @Override public Coupon getById(Long id) { return findById(id).orElseThrow(); }
            @Override @SuppressWarnings("unchecked") public <S extends Coupon> List<S> saveAll(Iterable<S> entities) { List<S> r = new ArrayList<>(); entities.forEach(e -> r.add((S) save(e))); return r; }
            @Override public void deleteById(Long id) {}
            @Override public void deleteAllById(Iterable<? extends Long> ids) {}
            @Override public void deleteAll(Iterable<? extends Coupon> entities) {}
            @Override public List<Coupon> findAll(org.springframework.data.domain.Sort sort) { return findAll(); }
            @Override public org.springframework.data.domain.Page<Coupon> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
            @Override public <S extends Coupon> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
            @Override public <S extends Coupon> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
            @Override public <S extends Coupon> long count(org.springframework.data.domain.Example<S> example) { return 0; }
            @Override public <S extends Coupon> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
            @Override public <S extends Coupon, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        };
    }

    private UserCouponRepository createFakeUserCouponRepository() {
        return new UserCouponRepository() {
            @Override
            public Optional<UserCoupon> findById(Long id) {
                return Optional.ofNullable(userCouponStore.get(id));
            }

            @Override
            public UserCoupon save(UserCoupon userCoupon) {
                if (userCoupon.getId() == null) {
                    Long newId = userCouponIdGenerator.getAndIncrement();
                    userCoupon = UserCoupon.builder()
                        .id(newId)
                        .user(userCoupon.getUser())
                        .coupon(userCoupon.getCoupon())
                        .status(userCoupon.getStatus())
                        .issuedAt(userCoupon.getIssuedAt())
                        .usedAt(userCoupon.getUsedAt())
                        .build();
                }
                userCouponStore.put(userCoupon.getId(), userCoupon);
                return userCoupon;
            }

            @Override
            public Long countByUserAndCoupon(User user, Coupon coupon) {
                return userCouponStore.values().stream()
                    .filter(uc -> uc.getUser().getId().equals(user.getId()))
                    .filter(uc -> uc.getCoupon().getId().equals(coupon.getId()))
                    .count();
            }

            @Override
            public List<UserCoupon> findByUserOrderByIssuedAtDesc(User user) {
                return userCouponStore.values().stream()
                    .filter(uc -> uc.getUser().getId().equals(user.getId()))
                    .sorted(Comparator.comparing(UserCoupon::getIssuedAt).reversed())
                    .collect(Collectors.toList());
            }

            @Override
            public List<UserCoupon> findAvailableCouponsByUser(User user, LocalDateTime now) {
                return userCouponStore.values().stream()
                    .filter(uc -> uc.getUser().getId().equals(user.getId()))
                    .filter(UserCoupon::canUse)
                    .collect(Collectors.toList());
            }

            public Optional<UserCoupon> findByUserAndCoupon(User user, Coupon coupon) {
                return userCouponStore.values().stream()
                    .filter(uc -> uc.getUser().getId().equals(user.getId()))
                    .filter(uc -> uc.getCoupon().getId().equals(coupon.getId()))
                    .findFirst();
            }

            @Override
            public List<UserCoupon> findAll() {
                return new ArrayList<>(userCouponStore.values());
            }

            @Override
            public void delete(UserCoupon userCoupon) {
                userCouponStore.remove(userCoupon.getId());
            }

            @Override
            public void deleteAll() {
                userCouponStore.clear();
            }

            @Override
            public long count() {
                return userCouponStore.size();
            }

            @Override
            public List<UserCoupon> findExpiredCoupons(LocalDateTime now) {
                return userCouponStore.values().stream()
                    .filter(uc -> uc.getStatus() == UserCouponStatus.ISSUED)
                    .filter(uc -> uc.getCoupon().getValidUntil().isBefore(now))
                    .collect(Collectors.toList());
            }

            @Override
            public List<UserCoupon> findByUserAndStatus(User user, UserCouponStatus status) {
                return userCouponStore.values().stream()
                    .filter(uc -> uc.getUser().getId().equals(user.getId()))
                    .filter(uc -> uc.getStatus() == status)
                    .collect(Collectors.toList());
            }

            public Page<UserCoupon> findByUser(User user, Pageable pageable) {
                List<UserCoupon> filtered = userCouponStore.values().stream()
                    .filter(uc -> uc.getUser().getId().equals(user.getId()))
                    .collect(Collectors.toList());
                return new PageImpl<>(filtered, pageable, filtered.size());
            }

            // JpaRepository stub methods
            @Override public <S extends UserCoupon> List<S> findAll(org.springframework.data.domain.Example<S> example) { return new ArrayList<>(); }
            @Override public <S extends UserCoupon> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return new ArrayList<>(); }
            @Override public UserCoupon getReferenceById(Long id) { return findById(id).orElse(null); }
            @Override public void flush() {}
            @Override @SuppressWarnings("unchecked") public <S extends UserCoupon> S saveAndFlush(S entity) { return (S) save(entity); }
            @Override @SuppressWarnings("unchecked") public <S extends UserCoupon> List<S> saveAllAndFlush(Iterable<S> entities) { List<S> r = new ArrayList<>(); entities.forEach(e -> r.add((S) save(e))); return r; }
            @Override public void deleteAllInBatch(Iterable<UserCoupon> entities) {}
            @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
            @Override public void deleteAllInBatch() {}
            @Override public UserCoupon getOne(Long id) { return findById(id).orElse(null); }
            @Override public UserCoupon getById(Long id) { return findById(id).orElseThrow(); }
            @Override @SuppressWarnings("unchecked") public <S extends UserCoupon> List<S> saveAll(Iterable<S> entities) { List<S> r = new ArrayList<>(); entities.forEach(e -> r.add((S) save(e))); return r; }
            @Override public void deleteById(Long id) {}
            @Override public void deleteAllById(Iterable<? extends Long> ids) {}
            @Override public void deleteAll(Iterable<? extends UserCoupon> entities) {}
            @Override public List<UserCoupon> findAllById(Iterable<Long> ids) { return new ArrayList<>(); }
            @Override public List<UserCoupon> findAll(org.springframework.data.domain.Sort sort) { return findAll(); }
            @Override public org.springframework.data.domain.Page<UserCoupon> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
            @Override public <S extends UserCoupon> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
            @Override public <S extends UserCoupon> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
            @Override public <S extends UserCoupon> long count(org.springframework.data.domain.Example<S> example) { return 0; }
            @Override public <S extends UserCoupon> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
            @Override public <S extends UserCoupon, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
            @Override public boolean existsById(Long id) { return userCouponStore.containsKey(id); }
        };
    }

    private UserRepository createFakeUserRepository() {
        return new UserRepository() {
            @Override
            public Optional<User> findById(Long id) {
                return Optional.ofNullable(userStore.get(id));
            }

            @Override
            public Optional<User> findByIdWithLock(Long id) {
                return Optional.ofNullable(userStore.get(id));
            }

            @Override
            public Optional<User> findByEmail(String email) {
                return userStore.values().stream()
                    .filter(u -> u.getEmail().equals(email))
                    .findFirst();
            }

            @Override
            public User save(User user) {
                userStore.put(user.getId(), user);
                return user;
            }

            @Override
            public List<User> findAll() {
                return new ArrayList<>(userStore.values());
            }

            @Override
            public void delete(User user) {
                userStore.remove(user.getId());
            }

            @Override
            public void deleteAll() {
                userStore.clear();
            }

            @Override
            public long count() {
                return userStore.size();
            }

            @Override
            public boolean existsById(Long id) {
                return userStore.containsKey(id);
            }

            @Override
            public boolean existsByEmail(String email) {
                return userStore.values().stream()
                    .anyMatch(u -> u.getEmail().equals(email));
            }

            // JpaRepository stub methods
            @Override public void flush() {}
            @Override @SuppressWarnings("unchecked") public <S extends User> S saveAndFlush(S entity) { return (S) save(entity); }
            @Override @SuppressWarnings("unchecked") public <S extends User> List<S> saveAllAndFlush(Iterable<S> entities) { List<S> result = new ArrayList<>(); entities.forEach(e -> result.add((S) save(e))); return result; }
            @Override public void deleteAllInBatch(Iterable<User> entities) { entities.forEach(this::delete); }
            @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
            @Override public void deleteAllInBatch() { deleteAll(); }
            @Override public User getOne(Long id) { return findById(id).orElse(null); }
            @Override public User getById(Long id) { return findById(id).orElseThrow(); }
            @Override public User getReferenceById(Long id) { return getById(id); }
            @Override @SuppressWarnings("unchecked") public <S extends User> List<S> saveAll(Iterable<S> entities) { List<S> result = new ArrayList<>(); entities.forEach(e -> result.add((S) save(e))); return result; }
            @Override public void deleteById(Long id) { userStore.remove(id); }
            @Override public void deleteAllById(Iterable<? extends Long> ids) { ids.forEach(this::deleteById); }
            @Override public void deleteAll(Iterable<? extends User> entities) { entities.forEach(this::delete); }
            @Override public List<User> findAllById(Iterable<Long> ids) { List<User> result = new ArrayList<>(); ids.forEach(id -> findById(id).ifPresent(result::add)); return result; }
            @Override public List<User> findAll(org.springframework.data.domain.Sort sort) { return findAll(); }
            @Override public org.springframework.data.domain.Page<User> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
            @Override public <S extends User> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
            @Override public <S extends User> List<S> findAll(org.springframework.data.domain.Example<S> example) { return new ArrayList<>(); }
            @Override public <S extends User> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return new ArrayList<>(); }
            @Override public <S extends User> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
            @Override public <S extends User> long count(org.springframework.data.domain.Example<S> example) { return 0; }
            @Override public <S extends User> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
            @Override public <S extends User, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        };
    }

    // ========================================
    // 테스트 데이터 생성 헬퍼 메서드
    // ========================================

    private User createUser(Long id, String email, String name) {
        return User.builder()
            .id(id)
            .email(email)
            .password("password")
            .name(name)
            .balance(BigDecimal.valueOf(100000))
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .build();
    }

    private Category createCategory(Long id, String name) {
        return Category.builder()
            .id(id)
            .name(name)
            .description("테스트 카테고리")
            .createdAt(LocalDateTime.now())
            .build();
    }

    private Coupon createCoupon(Long id, String code, int totalQuantity, int issuedQuantity) {
        return Coupon.builder()
            .id(id)
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
    }

    private Coupon createCouponWithIssueTime(
            Long id,
            String code,
            int totalQuantity,
            int issuedQuantity,
            LocalDateTime issueStartAt,
            LocalDateTime issueEndAt) {

        return Coupon.builder()
            .id(id)
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
    }

    private UserCoupon createUserCoupon(Long id, User user, Coupon coupon) {
        return UserCoupon.builder()
            .id(id)
            .user(user)
            .coupon(coupon)
            .status(UserCouponStatus.ISSUED)
            .issuedAt(LocalDateTime.now())
            .build();
    }
}

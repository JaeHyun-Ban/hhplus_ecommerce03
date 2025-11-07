package com.hhplus.ecommerce.infrastructure.persistence.coupon;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import com.hhplus.ecommerce.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자 쿠폰 Repository
 *
 * Infrastructure Layer - 데이터베이스 접근 계층
 *
 * 책임:
 * - 사용자별 쿠폰 CRUD 연산
 * - 사용 가능한 쿠폰 조회
 * - 발급 이력 관리
 *
 * Use Cases:
 * - UC-017: 쿠폰 발급
 * - UC-018: 내 쿠폰 목록 조회
 * - UC-019: 쿠폰 사용 (주문 시)
 */
@Repository
public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    /**
     * 사용자의 사용 가능한 쿠폰 목록 조회
     *
     * Use Case:
     * - UC-018: 내 쿠폰 목록 조회
     * - UC-012: 주문 시 사용 가능한 쿠폰 목록 표시
     *
     * 사용 가능 조건:
     * - status = ISSUED (발급됨)
     * - 유효 기간 내 (validFrom <= NOW <= validUntil)
     *
     * 성능 최적화:
     * - idx_user_coupons_user_status 복합 인덱스 사용
     *
     * @param user 사용자
     * @param now 현재 시각
     * @return 사용 가능한 쿠폰 목록
     */
    @Query("SELECT uc FROM UserCoupon uc " +
           "JOIN FETCH uc.coupon c " +
           "WHERE uc.user = :user " +
           "AND uc.status = 'ISSUED' " +
           "AND c.validFrom <= :now " +
           "AND c.validUntil >= :now " +
           "ORDER BY c.validUntil ASC")
    List<UserCoupon> findAvailableCouponsByUser(
        @Param("user") User user,
        @Param("now") LocalDateTime now
    );

    /**
     * 사용자별 쿠폰 목록 조회 (전체)
     *
     * Use Case:
     * - UC-018: 내 쿠폰 목록 조회 (사용 완료, 만료 포함)
     *
     * @param user 사용자
     * @return 쿠폰 목록
     */
    @Query("SELECT uc FROM UserCoupon uc " +
           "JOIN FETCH uc.coupon " +
           "WHERE uc.user = :user " +
           "ORDER BY uc.issuedAt DESC")
    List<UserCoupon> findByUserOrderByIssuedAtDesc(@Param("user") User user);

    /**
     * 사용자의 특정 쿠폰 발급 횟수 조회
     *
     * Use Case:
     * - UC-017: 쿠폰 발급 시 1인당 발급 제한 확인
     *
     * 발급 제한:
     * - Coupon.maxIssuePerUser: 1인당 최대 발급 횟수
     * - 예: 1인당 1회 제한 쿠폰
     *
     * @param user 사용자
     * @param coupon 쿠폰
     * @return 발급 횟수
     */
    @Query("SELECT COUNT(uc) FROM UserCoupon uc " +
           "WHERE uc.user = :user AND uc.coupon = :coupon")
    Long countByUserAndCoupon(@Param("user") User user, @Param("coupon") Coupon coupon);

    /**
     * 사용자별 특정 상태의 쿠폰 목록 조회
     *
     * Use Case:
     * - 통계: 사용 완료 쿠폰 조회
     *
     * @param user 사용자
     * @param status 쿠폰 상태
     * @return 쿠폰 목록
     */
    @Query("SELECT uc FROM UserCoupon uc " +
           "WHERE uc.user = :user AND uc.status = :status")
    List<UserCoupon> findByUserAndStatus(
        @Param("user") User user,
        @Param("status") UserCouponStatus status
    );

    /**
     * 만료된 쿠폰 목록 조회
     *
     * Use Case:
     * - 배치 작업: 만료 쿠폰 상태 자동 업데이트
     *
     * @param now 현재 시각
     * @return 만료된 쿠폰 목록
     */
    @Query("SELECT uc FROM UserCoupon uc " +
           "JOIN FETCH uc.coupon c " +
           "WHERE uc.status = 'ISSUED' " +
           "AND c.validUntil < :now")
    List<UserCoupon> findExpiredCoupons(@Param("now") LocalDateTime now);
}

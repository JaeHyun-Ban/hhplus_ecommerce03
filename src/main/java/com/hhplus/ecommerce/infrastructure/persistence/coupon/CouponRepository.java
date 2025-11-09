package com.hhplus.ecommerce.infrastructure.persistence.coupon;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 Repository
 *
 * Infrastructure Layer - 데이터베이스 접근 계층
 *
 * 책임:
 * - 쿠폰 마스터 CRUD 연산
 * - 낙관적 락을 이용한 선착순 발급 동시성 제어
 * - 발급 가능한 쿠폰 목록 조회
 *
 * Use Cases:
 * - UC-017: 쿠폰 발급 (선착순)
 * - UC-018: 발급 가능한 쿠폰 목록 조회
 */
@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /**
     * 쿠폰 ID로 조회 (낙관적 락)
     *
     * Use Case:
     * - UC-017: 선착순 쿠폰 발급 시 동시성 제어
     *
     * 락 전략:
     * - OPTIMISTIC: @Version 필드를 이용한 낙관적 락
     * - issuedQuantity 증가 시 version 불일치 시 OptimisticLockException 발생
     * - 동시 발급 시도 시 한 명만 성공, 나머지는 재시도
     *
     * 선착순 보장:
     * - 발급 수량(issuedQuantity)이 총 수량(totalQuantity)에 도달하면 마감
     * - 낙관적 락으로 정확한 수량 제어
     *
     * @param id 쿠폰 ID
     * @return 쿠폰 엔티티 (Optional)
     */
    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdWithLock(@Param("id") Long id);

    /**
     * 쿠폰 코드로 조회
     *
     * Use Case:
     * - UC-017: 쿠폰 코드로 발급 (프로모션 코드 입력)
     *
     * @param code 쿠폰 코드 (unique)
     * @return 쿠폰 엔티티 (Optional)
     */
    Optional<Coupon> findByCode(String code);

    /**
     * 현재 발급 가능한 쿠폰 목록 조회
     *
     * Use Case:
     * - UC-018: 쿠폰 목록 조회
     *
     * 발급 가능 조건:
     * - status = ACTIVE (활성 상태)
     * - 발급 기간 내 (issueStartAt <= NOW <= issueEndAt)
     * - 발급 수량 남음 (issuedQuantity < totalQuantity)
     *
     * 성능 최적화:
     * - idx_coupons_status_issue_dates 복합 인덱스 사용
     *
     * @param now 현재 시각
     * @return 발급 가능한 쿠폰 목록
     */
    @Query("SELECT c FROM Coupon c " +
           "WHERE c.status = 'ACTIVE' " +
           "AND c.issueStartAt <= :now " +
           "AND c.issueEndAt >= :now " +
           "AND c.issuedQuantity < c.totalQuantity " +
           "ORDER BY c.issueEndAt ASC")
    List<Coupon> findAvailableCoupons(@Param("now") LocalDateTime now);

    /**
     * 특정 카테고리에 적용 가능한 쿠폰 목록 조회
     *
     * Use Case:
     * - UC-018: 카테고리별 쿠폰 조회
     * - UC-012: 주문 시 적용 가능한 쿠폰 조회
     *
     * @param categoryId 카테고리 ID
     * @param now 현재 시각
     * @return 쿠폰 목록
     */
    @Query("SELECT c FROM Coupon c " +
           "WHERE c.status = 'ACTIVE' " +
           "AND c.applicableCategory.id = :categoryId " +
           "AND c.issueStartAt <= :now " +
           "AND c.issueEndAt >= :now " +
           "AND c.issuedQuantity < c.totalQuantity")
    List<Coupon> findAvailableCouponsByCategory(
        @Param("categoryId") Long categoryId,
        @Param("now") LocalDateTime now
    );

    /**
     * 특정 상태의 쿠폰 목록 조회
     *
     * Use Case:
     * - 관리자 기능: 쿠폰 상태별 관리
     *
     * @param status 쿠폰 상태
     * @return 쿠폰 목록
     */
    List<Coupon> findByStatus(CouponStatus status);

    /**
     * 발급 마감된 쿠폰 목록 조회
     *
     * Use Case:
     * - 배치 작업: 쿠폰 상태 자동 업데이트
     *
     * @return 발급 마감 쿠폰 목록
     */
    @Query("SELECT c FROM Coupon c " +
           "WHERE c.status = 'ACTIVE' " +
           "AND c.issuedQuantity >= c.totalQuantity")
    List<Coupon> findSoldOutCoupons();
}

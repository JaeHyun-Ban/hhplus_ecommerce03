package com.hhplus.ecommerce.infrastructure.persistence.user;

import com.hhplus.ecommerce.domain.user.BalanceHistory;
import com.hhplus.ecommerce.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 잔액 이력 Repository
 *
 * Infrastructure Layer - 데이터베이스 접근 계층
 *
 * 책임:
 * - 잔액 변동 이력 저장 및 조회
 * - 사용자별 잔액 이력 조회
 *
 * Use Cases:
 * - UC-001: 잔액 충전 이력 기록
 * - UC-002: 잔액 조회 시 이력 조회
 * - UC-012: 주문 결제 시 이력 기록
 */
@Repository
public interface BalanceHistoryRepository extends JpaRepository<BalanceHistory, Long> {

    /**
     * 사용자별 잔액 이력 조회 (최신순)
     *
     * Use Case:
     * - UC-002: 잔액 조회 시 최근 이력 조회
     *
     * 성능 최적화:
     * - idx_balance_histories_user_created 인덱스 사용
     *
     * @param user 사용자
     * @param pageable 페이징 정보
     * @return 잔액 이력 페이지
     */
    @Query("SELECT bh FROM BalanceHistory bh WHERE bh.user = :user ORDER BY bh.createdAt DESC")
    Page<BalanceHistory> findByUserOrderByCreatedAtDesc(@Param("user") User user, Pageable pageable);

    /**
     * 사용자별 특정 기간 잔액 이력 조회
     *
     * Use Case:
     * - 관리자 기능: 사용자 잔액 변동 분석
     *
     * @param user 사용자
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 잔액 이력 목록
     */
    @Query("SELECT bh FROM BalanceHistory bh WHERE bh.user = :user " +
           "AND bh.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY bh.createdAt DESC")
    List<BalanceHistory> findByUserAndCreatedAtBetween(
        @Param("user") User user,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
}

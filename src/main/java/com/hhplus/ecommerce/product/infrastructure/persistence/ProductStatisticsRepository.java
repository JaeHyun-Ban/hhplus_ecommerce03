package com.hhplus.ecommerce.product.infrastructure.persistence;

import com.hhplus.ecommerce.product.domain.ProductStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 상품 통계 Repository
 *
 * Infrastructure Layer - 데이터베이스 접근 계층
 *
 * 책임:
 * - 상품별 일일 통계 저장 및 조회
 * - 인기 상품 조회 (최근 3일 판매량 기준)
 *
 * Use Cases:
 * - UC-006: 인기 상품 조회
 * - 배치 작업: 일일 통계 집계
 */
@Repository
public interface ProductStatisticsRepository extends JpaRepository<ProductStatistics, Long> {

    /**
     * 최근 3일간 판매량 TOP 상품 조회
     *
     * Use Case:
     * - UC-006: 인기 상품 조회 (최근 3일 판매량 기준 상위 5개)
     *
     * 집계 기준:
     * - 최근 3일 (통계일자 기준)
     * - 판매량(salesCount) 합계 기준 정렬
     * - 상위 5개 상품
     *
     * 성능 최적화:
     * - idx_product_statistics_date 인덱스 사용
     * - GROUP BY로 상품별 집계
     *
     * @param startDate 시작일 (3일 전)
     * @param endDate 종료일 (오늘)
     * @param limit 조회 개수 (5)
     * @return 인기 상품 ID 목록
     */
    @Query("SELECT ps.product.id " +
           "FROM ProductStatistics ps " +
           "WHERE ps.statisticsDate BETWEEN :startDate AND :endDate " +
           "GROUP BY ps.product.id " +
           "ORDER BY SUM(ps.salesCount) DESC")
    List<Long> findTopProductIdsByDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("limit") int limit
    );

    /**
     * 특정 상품의 특정 날짜 통계 조회
     *
     * Use Case:
     * - 배치 작업: 일일 통계 중복 방지
     *
     * @param productId 상품 ID
     * @param date 통계 날짜
     * @return 상품 통계 (Optional)
     */
    @Query("SELECT ps FROM ProductStatistics ps " +
           "WHERE ps.product.id = :productId AND ps.statisticsDate = :date")
    Optional<ProductStatistics> findByProductIdAndDate(
        @Param("productId") Long productId,
        @Param("date") LocalDate date
    );

    /**
     * 특정 상품의 기간별 통계 조회
     *
     * Use Case:
     * - 관리자 기능: 상품 판매 추이 분석
     *
     * @param productId 상품 ID
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 통계 목록
     */
    @Query("SELECT ps FROM ProductStatistics ps " +
           "WHERE ps.product.id = :productId " +
           "AND ps.statisticsDate BETWEEN :startDate AND :endDate " +
           "ORDER BY ps.statisticsDate DESC")
    List<ProductStatistics> findByProductIdAndDateRange(
        @Param("productId") Long productId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}

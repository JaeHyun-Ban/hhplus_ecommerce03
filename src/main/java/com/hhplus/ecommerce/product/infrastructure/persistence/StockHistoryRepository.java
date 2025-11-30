package com.hhplus.ecommerce.product.infrastructure.persistence;

import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.domain.StockHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 재고 이력 Repository
 *
 * Infrastructure Layer - 데이터베이스 접근 계층
 *
 * 책임:
 * - 재고 변동 이력 저장 및 조회
 * - 상품별 재고 이력 추적
 *
 * Use Cases:
 * - UC-012: 주문 생성 시 재고 감소 이력 기록
 * - UC-015: 주문 취소 시 재고 복구 이력 기록
 * - 관리자 기능: 재고 변동 추적
 */
@Repository
public interface StockHistoryRepository extends JpaRepository<StockHistory, Long> {

    /**
     * 상품별 재고 이력 조회 (최신순, 페이징)
     *
     * Use Case:
     * - 관리자 기능: 상품 재고 변동 이력 조회
     *
     * @param product 상품
     * @param pageable 페이징 정보
     * @return 재고 이력 페이지
     */
    @Query("SELECT sh FROM StockHistory sh " +
           "WHERE sh.product = :product " +
           "ORDER BY sh.createdAt DESC")
    Page<StockHistory> findByProductOrderByCreatedAtDesc(
        @Param("product") Product product,
        Pageable pageable
    );

    /**
     * 상품별 특정 기간 재고 이력 조회
     *
     * Use Case:
     * - 관리자 기능: 기간별 재고 변동 분석
     *
     * @param product 상품
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 재고 이력 목록
     */
    @Query("SELECT sh FROM StockHistory sh " +
           "WHERE sh.product = :product " +
           "AND sh.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY sh.createdAt DESC")
    List<StockHistory> findByProductAndCreatedAtBetween(
        @Param("product") Product product,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
}

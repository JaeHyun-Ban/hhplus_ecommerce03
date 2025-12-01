package com.hhplus.ecommerce.product.infrastructure.persistence;

import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.domain.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * 상품 Repository
 *
 * Infrastructure Layer - 데이터베이스 접근 계층
 *
 * 책임:
 * - 상품 엔티티 CRUD 연산
 * - 낙관적 락을 이용한 동시성 제어 (재고 차감 시)
 * - 상품 목록 조회 (필터링, 페이징)
 *
 * Use Cases:
 * - UC-003: 상품 목록 조회
 * - UC-004: 상품 상세 조회
 * - UC-005: 재고 차감
 * - UC-012: 주문 생성 (재고 차감)
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 상품 ID로 조회 (낙관적 락)
     *
     * Use Case:
     * - UC-012: 주문 생성 시 재고 차감 동시성 제어
     *
     * 락 전략:
     * - OPTIMISTIC: @Version 필드를 이용한 낙관적 락
     * - 재고 차감 시 version 불일치 시 OptimisticLockException 발생
     * - 재시도를 통해 동시성 제어
     *
     * @param id 상품 ID
     * @return 상품 엔티티 (Optional)
     */
    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);

    /**
     * 판매 가능한 상품 목록 조회 (페이징)
     *
     * Use Case:
     * - UC-003: 상품 목록 조회
     *
     * 필터:
     * - status = AVAILABLE (판매 가능)
     * - stock > 0 (재고 있음)
     *
     * 성능 최적화:
     * - idx_products_status_stock 복합 인덱스 사용
     *
     * @param pageable 페이징 정보
     * @return 상품 페이지
     */
    @Query("SELECT p FROM Product p WHERE p.status = 'AVAILABLE' AND p.stock > 0 " +
           "ORDER BY p.createdAt DESC")
    Page<Product> findAvailableProducts(Pageable pageable);

    /**
     * 카테고리별 상품 목록 조회 (페이징)
     *
     * Use Case:
     * - UC-003: 카테고리별 상품 목록 조회
     *
     * @param categoryId 카테고리 ID
     * @param pageable 페이징 정보
     * @return 상품 페이지
     */
    @Query("SELECT p FROM Product p WHERE p.category.id = :categoryId " +
           "AND p.status = 'AVAILABLE' AND p.stock > 0 " +
           "ORDER BY p.createdAt DESC")
    Page<Product> findByCategoryId(@Param("categoryId") Long categoryId, Pageable pageable);

    /**
     * 안전 재고 이하 상품 목록 조회
     *
     * Use Case:
     * - 관리자 기능: 재고 부족 상품 알림
     * - UC-020: 재입고 알림 대상 상품 조회
     *
     * @return 안전 재고 이하 상품 목록
     */
    @Query("SELECT p FROM Product p WHERE p.stock <= p.safetyStock " +
           "AND p.status != 'DISCONTINUED' " +
           "ORDER BY p.stock ASC")
    List<Product> findLowStockProducts();

    /**
     * 특정 상태의 상품 목록 조회
     *
     * Use Case:
     * - 관리자 기능: 상품 상태별 관리
     *
     * @param status 상품 상태
     * @return 상품 목록
     */
    List<Product> findByStatus(ProductStatus status);

    /**
     * 상품명으로 조회 (성능 테스트용)
     *
     * Use Case:
     * - 인덱스 성능 측정
     * - idx_name 인덱스 활용
     *
     * @param name 상품명
     * @return 상품 엔티티 (Optional)
     */
    Optional<Product> findByName(String name);
}

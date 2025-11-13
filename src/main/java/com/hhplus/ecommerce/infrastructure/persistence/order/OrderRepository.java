package com.hhplus.ecommerce.infrastructure.persistence.order;

import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderStatus;
import com.hhplus.ecommerce.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 주문 Repository
 *
 * Infrastructure Layer - 데이터베이스 접근 계층
 *
 * 책임:
 * - 주문 엔티티 CRUD 연산
 * - 멱등성 키 기반 중복 주문 방지
 * - 주문 번호 생성을 위한 일일 주문 수 조회
 *
 * Use Cases:
 * - UC-012: 주문 생성 및 결제
 * - UC-013: 주문 상세 조회
 * - UC-014: 주문 목록 조회
 * - UC-015: 주문 취소
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * 멱등성 키로 주문 조회
     *
     * Use Case:
     * - UC-012 Step 12: 중복 결제 방지
     *
     * 멱등성 보장:
     * - 동일한 idempotencyKey로 재요청 시 기존 주문 반환
     * - 네트워크 재시도, 중복 클릭 등으로 인한 중복 결제 방지
     *
     * 성능 최적화:
     * - uk_orders_idempotency 유니크 인덱스 사용
     *
     * @param idempotencyKey 멱등성 키 (UUID)
     * @return 주문 엔티티 (Optional)
     */
    @Query("SELECT o FROM Order o WHERE o.idempotencyKey = :key")
    Optional<Order> findByIdempotencyKey(@Param("key") String idempotencyKey);

    /**
     * 주문 번호로 조회
     *
     * Use Case:
     * - UC-013: 주문 상세 조회 (주문번호 기반)
     *
     * @param orderNumber 주문 번호 (예: ORD-20251105-000001)
     * @return 주문 엔티티 (Optional)
     */
    @Query("SELECT o FROM Order o WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumber(@Param("orderNumber") String orderNumber);

    /**
     * 특정 시간 범위의 주문 수 조회
     *
     * Use Case:
     * - UC-012 Step 13.4: 주문 번호 생성 (일련번호 계산)
     *
     * 주문 번호 형식:
     * - ORD-YYYYMMDD-NNNNNN
     * - NNNNNN: 당일 순번 (000001부터 시작)
     *
     * @param startOfDay 시작 시간
     * @param endOfDay 종료 시간
     * @return 해당 시간 범위의 주문 수
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.orderedAt >= :startOfDay AND o.orderedAt < :endOfDay")
    Long countOrdersBetween(@Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    /**
     * 사용자별 주문 목록 조회 (최신순, 페이징)
     *
     * Use Case:
     * - UC-014: 주문 목록 조회
     *
     * 성능 최적화:
     * - idx_orders_user_ordered 복합 인덱스 사용
     *
     * @param user 사용자
     * @param pageable 페이징 정보
     * @return 주문 페이지
     */
    @Query("SELECT o FROM Order o WHERE o.user = :user ORDER BY o.orderedAt DESC")
    Page<Order> findByUserOrderByOrderedAtDesc(@Param("user") User user, Pageable pageable);

    /**
     * 사용자별 특정 상태의 주문 목록 조회
     *
     * Use Case:
     * - UC-014: 주문 목록 조회 (상태 필터링)
     *
     * @param user 사용자
     * @param status 주문 상태
     * @param pageable 페이징 정보
     * @return 주문 페이지
     */
    @Query("SELECT o FROM Order o WHERE o.user = :user AND o.status = :status " +
           "ORDER BY o.orderedAt DESC")
    Page<Order> findByUserAndStatus(
        @Param("user") User user,
        @Param("status") OrderStatus status,
        Pageable pageable
    );

    /**
     * ID로 주문 조회 (연관 엔티티 Fetch Join)
     *
     * Use Case:
     * - UC-013: 주문 상세 조회 (N+1 문제 방지)
     *
     * Fetch Join:
     * - orderItems: 주문 항목
     * - orderItems.product: 상품 정보
     *
     * Note: Multiple bags (orderItems, orderCoupons) cannot be fetch joined simultaneously
     * to avoid MultipleBagFetchException. orderCoupons should be fetched separately if needed.
     *
     * @param id 주문 ID
     * @return 주문 엔티티 (Optional)
     */
    @Query("SELECT o FROM Order o " +
           "LEFT JOIN FETCH o.orderItems oi " +
           "LEFT JOIN FETCH oi.product " +
           "WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") Long id);

    /**
     * 특정 기간 동안의 주문 목록 조회
     *
     * Use Case:
     * - 관리자 기능: 주문 통계 분석
     *
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 주문 목록
     */
    @Query("SELECT o FROM Order o WHERE o.orderedAt >= :startDate AND o.orderedAt < :endDate")
    List<Order> findByOrderedAtBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
}

package com.hhplus.ecommerce.product.infrastructure.persistence;

import com.hhplus.ecommerce.product.domain.NotificationStatus;
import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.domain.RestockNotification;
import com.hhplus.ecommerce.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 재입고 알림 Repository
 *
 * Infrastructure Layer - 데이터베이스 접근 계층
 *
 * 책임:
 * - 재입고 알림 신청 관리
 * - 발송 대기 알림 조회
 *
 * Use Cases:
 * - UC-020: 재입고 알림 신청
 * - UC-021: 재입고 알림 발송
 */
@Repository
public interface RestockNotificationRepository extends JpaRepository<RestockNotification, Long> {

    /**
     * 사용자 + 상품 + 상태로 알림 조회
     *
     * Use Case:
     * - UC-020: 재입고 알림 중복 신청 방지
     *
     * Unique 제약:
     * - (user_id, product_id, status) 복합 유니크
     * - 동일 사용자가 동일 상품에 대해 중복 신청 불가 (PENDING 상태)
     *
     * @param user 사용자
     * @param product 상품
     * @param status 알림 상태
     * @return 알림 (Optional)
     */
    Optional<RestockNotification> findByUserAndProductAndStatus(
        User user,
        Product product,
        NotificationStatus status
    );

    /**
     * 상품별 발송 대기 알림 목록 조회
     *
     * Use Case:
     * - UC-021: 재입고 시 알림 발송
     *
     * 발송 대상:
     * - status = PENDING (발송 대기)
     * - 신청 순서대로 발송 (requestedAt 오름차순)
     *
     * @param product 상품
     * @return 발송 대기 알림 목록
     */
    @Query("SELECT rn FROM RestockNotification rn " +
           "WHERE rn.product = :product " +
           "AND rn.status = 'PENDING' " +
           "ORDER BY rn.requestedAt ASC")
    List<RestockNotification> findPendingNotificationsByProduct(@Param("product") Product product);

    /**
     * 사용자별 알림 신청 목록 조회
     *
     * Use Case:
     * - 사용자 기능: 내 알림 신청 목록 조회
     *
     * @param user 사용자
     * @return 알림 목록
     */
    @Query("SELECT rn FROM RestockNotification rn " +
           "JOIN FETCH rn.product " +
           "WHERE rn.user = :user " +
           "ORDER BY rn.requestedAt DESC")
    List<RestockNotification> findByUserOrderByRequestedAtDesc(@Param("user") User user);

    /**
     * 사용자 + 상품으로 알림 존재 여부 확인
     *
     * Use Case:
     * - UC-020: 알림 신청 전 중복 체크 (최적화)
     *
     * @param user 사용자
     * @param product 상품
     * @return 존재 여부
     */
    @Query("SELECT CASE WHEN COUNT(rn) > 0 THEN true ELSE false END " +
           "FROM RestockNotification rn " +
           "WHERE rn.user = :user AND rn.product = :product AND rn.status = 'PENDING'")
    boolean existsByUserAndProductAndStatusPending(
        @Param("user") User user,
        @Param("product") Product product
    );
}

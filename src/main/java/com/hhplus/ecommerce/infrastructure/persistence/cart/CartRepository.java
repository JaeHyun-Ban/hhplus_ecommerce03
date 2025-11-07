package com.hhplus.ecommerce.infrastructure.persistence.cart;

import com.hhplus.ecommerce.domain.cart.Cart;
import com.hhplus.ecommerce.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 장바구니 Repository
 *
 * Infrastructure Layer - 데이터베이스 접근 계층
 *
 * 책임:
 * - 장바구니 엔티티 CRUD 연산
 * - 사용자별 장바구니 조회 (1:1 관계)
 *
 * Use Cases:
 * - UC-007: 장바구니 조회
 * - UC-008: 장바구니 상품 추가
 * - UC-011: 장바구니 비우기 (주문 완료 후)
 */
@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    /**
     * 사용자별 장바구니 조회
     *
     * Use Case:
     * - UC-007: 장바구니 조회
     * - UC-008: 장바구니 상품 추가 시 기존 장바구니 확인
     *
     * 관계:
     * - User : Cart = 1 : 1
     * - 사용자당 장바구니는 하나만 존재
     *
     * @param user 사용자
     * @return 장바구니 (Optional)
     */
    Optional<Cart> findByUser(User user);

    /**
     * 사용자별 장바구니 조회 (장바구니 항목 포함)
     *
     * Use Case:
     * - UC-007: 장바구니 조회 (N+1 문제 방지)
     * - UC-012: 주문 생성 시 장바구니 조회
     *
     * Fetch Join:
     * - cartItems: 장바구니 항목
     * - cartItems.product: 상품 정보 (가격 확인용)
     *
     * 성능 최적화:
     * - N+1 쿼리 문제 방지
     * - 한 번의 쿼리로 장바구니 + 항목 + 상품 정보 조회
     *
     * @param user 사용자
     * @return 장바구니 (Optional)
     */
    @Query("SELECT c FROM Cart c " +
           "LEFT JOIN FETCH c.items ci " +
           "LEFT JOIN FETCH ci.product " +
           "WHERE c.user = :user")
    Optional<Cart> findByUserWithItems(@Param("user") User user);

    /**
     * 사용자 ID로 장바구니 존재 여부 확인
     *
     * Use Case:
     * - UC-008: 장바구니 생성 전 존재 여부 확인 (최적화)
     *
     * @param userId 사용자 ID
     * @return 존재 여부
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
           "FROM Cart c WHERE c.user.id = :userId")
    boolean existsByUserId(@Param("userId") Long userId);
}

package com.hhplus.ecommerce.infrastructure.persistence.cart;

import com.hhplus.ecommerce.domain.cart.Cart;
import com.hhplus.ecommerce.domain.cart.CartItem;
import com.hhplus.ecommerce.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 장바구니 항목 Repository
 *
 * Infrastructure Layer - 데이터베이스 접근 계층
 *
 * 책임:
 * - 장바구니 항목 CRUD 연산
 * - 중복 상품 확인 (장바구니 + 상품 복합키)
 *
 * Use Cases:
 * - UC-008: 장바구니 상품 추가
 * - UC-009: 장바구니 수량 변경
 * - UC-010: 장바구니 항목 삭제
 */
@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /**
     * 장바구니에서 특정 상품 조회
     *
     * Use Case:
     * - UC-008: 장바구니 상품 추가 시 중복 확인
     *   - 이미 있으면: 수량만 증가
     *   - 없으면: 새로 추가
     *
     * Unique 제약조건:
     * - (cart_id, product_id) 복합 유니크
     * - 한 장바구니에 동일 상품 중복 불가
     *
     * @param cart 장바구니
     * @param product 상품
     * @return 장바구니 항목 (Optional)
     */
    Optional<CartItem> findByCartAndProduct(Cart cart, Product product);

    /**
     * 장바구니별 항목 목록 조회
     *
     * Use Case:
     * - UC-007: 장바구니 조회
     *
     * @param cart 장바구니
     * @return 장바구니 항목 목록
     */
    List<CartItem> findByCart(Cart cart);

    /**
     * 장바구니별 항목 목록 조회 (상품 정보 포함)
     *
     * Use Case:
     * - UC-007: 장바구니 조회 (N+1 방지)
     *
     * @param cart 장바구니
     * @return 장바구니 항목 목록
     */
    @Query("SELECT ci FROM CartItem ci " +
           "JOIN FETCH ci.product " +
           "WHERE ci.cart = :cart")
    List<CartItem> findByCartWithProduct(@Param("cart") Cart cart);

    /**
     * 장바구니의 모든 항목 삭제
     *
     * Use Case:
     * - UC-011: 장바구니 비우기
     * - UC-012: 주문 완료 후 장바구니 비우기
     *
     * @param cart 장바구니
     */
    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.cart = :cart")
    void deleteByCart(@Param("cart") Cart cart);

    /**
     * 장바구니 항목 수 조회
     *
     * Use Case:
     * - UI: 장바구니 아이콘 배지 표시
     *
     * @param cart 장바구니
     * @return 항목 수
     */
    @Query("SELECT COUNT(ci) FROM CartItem ci WHERE ci.cart = :cart")
    Long countByCart(@Param("cart") Cart cart);
}

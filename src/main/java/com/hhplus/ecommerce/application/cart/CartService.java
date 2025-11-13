package com.hhplus.ecommerce.application.cart;

import com.hhplus.ecommerce.domain.cart.Cart;
import com.hhplus.ecommerce.domain.cart.CartItem;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.infrastructure.persistence.cart.CartItemRepository;
import com.hhplus.ecommerce.infrastructure.persistence.cart.CartRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.ProductRepository;
import com.hhplus.ecommerce.infrastructure.persistence.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 장바구니 애플리케이션 서비스
 *
 * Application Layer - Use Case 실행 계층
 *
 * 책임:
 * - UC-007: 장바구니 조회
 * - UC-008: 장바구니 상품 추가
 * - UC-009: 장바구니 수량 변경
 * - UC-010: 장바구니 항목 삭제
 *
 * 레이어 의존성:
 * - Infrastructure Layer: CartRepository, CartItemRepository, ProductRepository, UserRepository
 * - Domain Layer: Cart, CartItem, Product, User
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    /**
     * 장바구니 조회
     *
     * Use Case: UC-007
     * - 사용자의 장바구니 + 항목 조회
     * - N+1 방지
     *
     * @param userId 사용자 ID
     * @return 장바구니
     */
    @Transactional
    public Cart getCart(Long userId) {
        log.info("[UC-007] 장바구니 조회 - userId: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        return cartRepository.findByUserWithItems(user)
            .orElseGet(() -> createEmptyCart(user));
    }

    /**
     * 장바구니 상품 추가
     *
     * Use Case: UC-008
     *
     * Main Flow:
     * 1. 사용자 장바구니 조회 (없으면 생성)
     * 2. 상품 조회 및 재고 확인
     * 3. 중복 상품 확인
     *    - 이미 있으면: 수량 증가
     *    - 없으면: 새로 추가
     * 4. 장바구니 갱신일시 업데이트
     *
     * @param userId 사용자 ID
     * @param productId 상품 ID
     * @param quantity 수량
     * @return 장바구니 항목
     */
    @Transactional
    public CartItem addToCart(Long userId, Long productId, Integer quantity) {
        log.info("[UC-008] 장바구니 추가 - userId: {}, productId: {}, quantity: {}",
                 userId, productId, quantity);

        // 입력 검증
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다");
        }

        // Step 1: 사용자 조회
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        // Step 2: 장바구니 조회 또는 생성
        Cart cart = cartRepository.findByUser(user)
            .orElseGet(() -> createEmptyCart(user));

        // Step 3: 상품 조회 및 재고 확인
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));

        if (!product.isAvailable()) {
            throw new IllegalStateException("판매 중인 상품이 아닙니다");
        }

        if (product.getStock() < quantity) {
            throw new IllegalStateException(
                String.format("재고가 부족합니다. 요청: %d개, 가능: %d개",
                    quantity, product.getStock())
            );
        }

        // Step 4: 중복 상품 확인
        CartItem cartItem = cartItemRepository.findByCartAndProduct(cart, product)
            .orElse(null);

        if (cartItem != null) {
            // 이미 있으면 수량 증가
            log.info("[UC-008] 기존 장바구니 항목 수량 증가 - cartItemId: {}, before: {}, add: {}",
                     cartItem.getId(), cartItem.getQuantity(), quantity);

            cartItem.updateQuantity(cartItem.getQuantity() + quantity);
        } else {
            // 없으면 새로 추가
            cartItem = CartItem.builder()
                .cart(cart)
                .product(product)
                .quantity(quantity)
                .priceAtAdd(product.getPrice()) // 현재 가격 스냅샷
                .build();

            cartItem = cartItemRepository.save(cartItem);

            log.info("[UC-008] 새 장바구니 항목 추가 - cartItemId: {}", cartItem.getId());
        }

        return cartItem;
    }

    /**
     * 장바구니 수량 변경
     *
     * Use Case: UC-009
     * - 수량 증가 또는 감소
     *
     * @param cartItemId 장바구니 항목 ID
     * @param quantity 변경할 수량
     */
    @Transactional
    public void updateCartItemQuantity(Long cartItemId, Integer quantity) {
        log.info("[UC-009] 장바구니 수량 변경 - cartItemId: {}, quantity: {}", cartItemId, quantity);

        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다");
        }

        CartItem cartItem = cartItemRepository.findById(cartItemId)
            .orElseThrow(() -> new IllegalArgumentException("장바구니 항목을 찾을 수 없습니다"));

        // 재고 확인
        Product product = cartItem.getProduct();
        if (product.getStock() < quantity) {
            throw new IllegalStateException(
                String.format("재고가 부족합니다. 요청: %d개, 가능: %d개",
                    quantity, product.getStock())
            );
        }

        cartItem.updateQuantity(quantity);
    }

    /**
     * 장바구니 항목 삭제
     *
     * Use Case: UC-010
     *
     * @param cartItemId 장바구니 항목 ID
     */
    @Transactional
    public void removeCartItem(Long cartItemId) {
        log.info("[UC-010] 장바구니 항목 삭제 - cartItemId: {}", cartItemId);

        CartItem cartItem = cartItemRepository.findById(cartItemId)
            .orElseThrow(() -> new IllegalArgumentException("장바구니 항목을 찾을 수 없습니다"));

        cartItemRepository.delete(cartItem);
    }

    /**
     * 장바구니 비우기
     *
     * Use Case: UC-016
     * - 장바구니의 모든 항목 삭제
     *
     * @param userId 사용자 ID
     */
    @Transactional
    public void clearCart(Long userId) {
        log.info("[UC-016] 장바구니 비우기 - userId: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        Cart cart = cartRepository.findByUser(user)
            .orElseThrow(() -> new IllegalArgumentException("장바구니를 찾을 수 없습니다"));

        // 도메인 메서드 호출
        cart.clear();
    }

    /**
     * 빈 장바구니 생성
     *
     * 책임:
     * - 사용자별 장바구니는 1:1
     * - 최초 조회 시 자동 생성
     *
     * @param user 사용자
     * @return 생성된 장바구니
     */
    private Cart createEmptyCart(User user) {
        log.info("빈 장바구니 생성 - userId: {}", user.getId());

        Cart cart = Cart.builder()
            .user(user)
            .build();

        return cartRepository.save(cart);
    }
}

package com.hhplus.ecommerce.cart.presentation.api;

import com.hhplus.ecommerce.cart.application.CartService;
import com.hhplus.ecommerce.cart.domain.Cart;
import com.hhplus.ecommerce.cart.domain.CartItem;
import com.hhplus.ecommerce.cart.presentation.api.dto.AddToCartRequest;
import com.hhplus.ecommerce.cart.presentation.api.dto.UpdateCartItemRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 장바구니 API 컨트롤러
 *
 * Presentation Layer - HTTP 요청/응답 처리 계층
 *
 * 책임:
 * - HTTP 요청 수신 및 응답 반환
 * - 장바구니 관련 API 제공
 *
 * Use Cases:
 * - UC-007: 장바구니 조회
 * - UC-008: 장바구니 상품 추가
 * - UC-009: 장바구니 수량 변경
 * - UC-010: 장바구니 항목 삭제
 */
@Tag(name = "Cart API", description = "장바구니 관련 API")
@Slf4j
@RestController
@RequestMapping("/api/carts")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * 장바구니 조회
     *
     * Use Case: UC-007
     * - GET /api/carts/{userId}
     *
     * @param userId 사용자 ID
     * @return 장바구니 정보 (항목 포함)
     */
    @Operation(summary = "장바구니 조회", description = "사용자의 장바구니를 조회합니다")
    @GetMapping("/{userId}")
    public ResponseEntity<Cart> getCart(@PathVariable Long userId) {
        log.info("[API] GET /api/carts/{}", userId);

        Cart cart = cartService.getCart(userId);

        return ResponseEntity.ok(cart);
    }

    /**
     * 장바구니 상품 추가
     *
     * Use Case: UC-008
     * - POST /api/carts/{userId}/items
     *
     * 동작:
     * - 이미 있는 상품: 수량 증가
     * - 없는 상품: 새로 추가
     *
     * @param userId 사용자 ID
     * @param request 추가 요청 (상품 ID, 수량)
     * @return 추가된 장바구니 항목 (201 Created)
     */
    @Operation(
        summary = "장바구니 상품 추가",
        description = "장바구니에 상품을 추가합니다. 이미 있는 상품은 수량이 증가합니다."
    )
    @PostMapping("/{userId}/items")
    public ResponseEntity<CartItem> addToCart(
            @PathVariable Long userId,
            @Valid @RequestBody AddToCartRequest request) {

        log.info("[API] POST /api/carts/{}/items - productId: {}, quantity: {}",
                 userId, request.getProductId(), request.getQuantity());

        CartItem cartItem = cartService.addToCart(
            userId,
            request.getProductId(),
            request.getQuantity()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(cartItem);
    }

    /**
     * 장바구니 수량 변경
     *
     * Use Case: UC-009
     * - PUT /api/carts/items/{cartItemId}
     *
     * @param cartItemId 장바구니 항목 ID
     * @param request 수량 변경 요청
     * @return 204 No Content
     */
    @Operation(summary = "장바구니 수량 변경", description = "장바구니 항목의 수량을 변경합니다")
    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<Void> updateCartItemQuantity(
            @PathVariable Long cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request) {

        log.info("[API] PUT /api/carts/items/{} - quantity: {}", cartItemId, request.getQuantity());

        cartService.updateCartItemQuantity(cartItemId, request.getQuantity());

        return ResponseEntity.noContent().build();
    }

    /**
     * 장바구니 항목 삭제
     *
     * Use Case: UC-010
     * - DELETE /api/carts/items/{cartItemId}
     *
     * @param cartItemId 장바구니 항목 ID
     * @return 204 No Content
     */
    @Operation(summary = "장바구니 항목 삭제", description = "장바구니에서 상품을 삭제합니다")
    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<Void> removeCartItem(@PathVariable Long cartItemId) {
        log.info("[API] DELETE /api/carts/items/{}", cartItemId);

        cartService.removeCartItem(cartItemId);

        return ResponseEntity.noContent().build();
    }

    /**
     * 장바구니 비우기
     *
     * Use Case: UC-016
     * - DELETE /api/carts/{userId}/items
     *
     * @param userId 사용자 ID
     * @return 204 No Content
     */
    @Operation(summary = "장바구니 비우기", description = "장바구니의 모든 항목을 삭제합니다")
    @DeleteMapping("/{userId}/items")
    public ResponseEntity<Void> clearCart(@PathVariable Long userId) {
        log.info("[API] DELETE /api/carts/{}/items", userId);

        cartService.clearCart(userId);

        return ResponseEntity.noContent().build();
    }
}

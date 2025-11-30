package com.hhplus.ecommerce.order.presentation.api;

import com.hhplus.ecommerce.order.application.OrderService;
import com.hhplus.ecommerce.order.domain.Order;
import com.hhplus.ecommerce.order.presentation.api.dto.CancelOrderRequest;
import com.hhplus.ecommerce.order.presentation.api.dto.CreateOrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 주문 API 컨트롤러
 *
 * Presentation Layer - HTTP 요청/응답 처리 계층
 *
 * 책임:
 * - HTTP 요청 수신 및 응답 반환
 * - 주문 관련 API 제공
 *
 * Use Cases:
 * - UC-012: 주문 생성 및 결제
 * - UC-013: 주문 상세 조회
 * - UC-014: 주문 목록 조회
 * - UC-015: 주문 취소
 */
@Tag(name = "Order API", description = "주문 관련 API")
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 주문 생성 및 결제
     *
     * Use Case: UC-012
     * - POST /api/orders
     *
     * 플로우:
     * 1. 장바구니 상품 확인
     * 2. 재고 및 잔액 검증
     * 3. 쿠폰 적용 (선택)
     * 4. 주문 생성 및 결제
     * 5. 장바구니 비우기
     *
     * @param request 주문 생성 요청
     * @return 생성된 주문 정보 (201 Created)
     */
    @Operation(
        summary = "주문 생성 및 결제",
        description = "장바구니의 상품을 주문하고 결제합니다. 멱등성 키를 통해 중복 결제를 방지합니다."
    )
    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("[API] POST /api/orders - userId: {}, idempotencyKey: {}",
                 request.getUserId(), request.getIdempotencyKey());

        // Application Layer 호출
        Order order = orderService.createOrder(
            request.getUserId(),
            request.getUserCouponId(),
            request.getIdempotencyKey()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /**
     * 주문 상세 조회
     *
     * Use Case: UC-013
     * - GET /api/orders/{orderId}
     *
     * @param orderId 주문 ID
     * @return 주문 상세 정보
     */
    @Operation(summary = "주문 상세 조회", description = "주문의 상세 정보를 조회합니다")
    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable Long orderId) {
        log.info("[API] GET /api/orders/{}", orderId);

        Order order = orderService.getOrder(orderId);

        return ResponseEntity.ok(order);
    }

    /**
     * 주문 번호로 조회
     *
     * Use Case: UC-013 (변형)
     * - GET /api/orders/by-number/{orderNumber}
     *
     * @param orderNumber 주문 번호 (예: ORD-20251105-000001)
     * @return 주문 정보
     */
    @Operation(summary = "주문 번호로 조회", description = "주문 번호로 주문 정보를 조회합니다")
    @GetMapping("/by-number/{orderNumber}")
    public ResponseEntity<Order> getOrderByNumber(@PathVariable String orderNumber) {
        log.info("[API] GET /api/orders/by-number/{}", orderNumber);

        Order order = orderService.getOrderByNumber(orderNumber);

        return ResponseEntity.ok(order);
    }

    /**
     * 사용자별 주문 목록 조회
     *
     * Use Case: UC-014
     * - GET /api/orders?userId={userId}
     *
     * @param userId 사용자 ID
     * @param pageable 페이징 정보 (기본: page=0, size=20)
     * @return 주문 목록 페이지
     */
    @Operation(
        summary = "사용자별 주문 목록 조회",
        description = "사용자의 주문 목록을 최신순으로 조회합니다"
    )
    @GetMapping
    public ResponseEntity<Page<Order>> getUserOrders(
            @RequestParam Long userId,
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("[API] GET /api/orders?userId={}", userId);

        Page<Order> orders = orderService.getUserOrders(userId, pageable);

        return ResponseEntity.ok(orders);
    }

    /**
     * 주문 취소
     *
     * Use Case: UC-015
     * - POST /api/orders/{orderId}/cancel
     *
     * 플로우:
     * 1. 주문 조회
     * 2. 취소 가능 여부 확인 (PAID 상태만 가능)
     * 3. 재고 복구
     * 4. 잔액 환불
     * 5. 주문 상태 변경 (CANCELLED)
     *
     * @param orderId 주문 ID
     * @param request 취소 요청 (취소 사유)
     * @return 204 No Content
     */
    @Operation(
        summary = "주문 취소",
        description = "결제 완료된 주문을 취소하고, 재고를 복구하고, 잔액을 환불합니다"
    )
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody CancelOrderRequest request) {

        log.info("[API] POST /api/orders/{}/cancel - reason: {}", orderId, request.getReason());

        // Application Layer 호출
        orderService.cancelOrder(orderId, request.getReason());

        return ResponseEntity.noContent().build();
    }
}

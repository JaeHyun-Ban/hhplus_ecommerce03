package com.hhplus.ecommerce.presentation.api.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 주문 생성 요청 DTO
 *
 * Use Case: UC-012
 * - 주문 생성 시 클라이언트가 전송하는 데이터
 */
@Schema(description = "주문 생성 요청")
@Getter
@NoArgsConstructor
public class CreateOrderRequest {

    /**
     * 사용자 ID
     */
    @Schema(description = "사용자 ID", example = "1", required = true)
    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;

    /**
     * 사용할 쿠폰 ID (선택 사항)
     */
    @Schema(description = "사용할 쿠폰 ID (선택)", example = "10")
    private Long userCouponId;

    /**
     * 멱등성 키 (중복 결제 방지)
     *
     * 클라이언트가 UUID 생성하여 전송
     * 동일한 키로 재요청 시 기존 주문 반환
     */
    @Schema(
        description = "멱등성 키 (UUID, 중복 결제 방지용)",
        example = "550e8400-e29b-41d4-a716-446655440000",
        required = true
    )
    @NotBlank(message = "멱등성 키는 필수입니다")
    private String idempotencyKey;
}

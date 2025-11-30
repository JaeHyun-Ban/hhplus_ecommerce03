package com.hhplus.ecommerce.cart.presentation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 장바구니 추가 요청 DTO
 *
 * Use Case: UC-008
 */
@Schema(description = "장바구니 추가 요청")
@Getter
@NoArgsConstructor
public class AddToCartRequest {

    @Schema(description = "상품 ID", example = "1", required = true)
    @NotNull(message = "상품 ID는 필수입니다")
    private Long productId;

    @Schema(description = "수량 (최소 1)", example = "2", required = true)
    @NotNull(message = "수량은 필수입니다")
    @Min(value = 1, message = "수량은 최소 1 이상이어야 합니다")
    private Integer quantity;
}

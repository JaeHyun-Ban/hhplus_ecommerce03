package com.hhplus.ecommerce.presentation.api.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * 장바구니 수량 변경 요청 DTO
 *
 * Use Case: UC-009
 */
@Schema(description = "장바구니 수량 변경 요청")
@Getter
@NoArgsConstructor
public class UpdateCartItemRequest {

    @Schema(description = "변경할 수량 (최소 1)", example = "3", required = true)
    @NotNull(message = "수량은 필수입니다")
    @Min(value = 1, message = "수량은 최소 1 이상이어야 합니다")
    private Integer quantity;
}

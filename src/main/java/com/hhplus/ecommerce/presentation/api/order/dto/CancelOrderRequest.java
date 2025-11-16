package com.hhplus.ecommerce.presentation.api.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 주문 취소 요청 DTO
 *
 * Use Case: UC-015
 * - 주문 취소 시 클라이언트가 전송하는 데이터
 */
@Schema(description = "주문 취소 요청")
@Getter
@NoArgsConstructor
public class CancelOrderRequest {

    /**
     * 취소 사유
     *
     * 제약사항:
     * - 필수 입력
     * - 최대 500자
     */
    @Schema(
        description = "취소 사유",
        example = "단순 변심",
        required = true
    )
    @NotBlank(message = "취소 사유는 필수입니다")
    @Size(max = 500, message = "취소 사유는 최대 500자까지 입력 가능합니다")
    private String reason;
}

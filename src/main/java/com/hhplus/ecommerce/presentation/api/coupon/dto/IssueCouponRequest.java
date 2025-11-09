package com.hhplus.ecommerce.presentation.api.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 쿠폰 발급 요청 DTO
 *
 * Use Case: UC-017 (선착순 쿠폰 발급)
 *
 * Validation:
 * - userId: 필수
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "쿠폰 발급 요청")
public class IssueCouponRequest {

    @NotNull(message = "사용자 ID는 필수입니다")
    @Schema(description = "사용자 ID", example = "1", required = true)
    private Long userId;
}
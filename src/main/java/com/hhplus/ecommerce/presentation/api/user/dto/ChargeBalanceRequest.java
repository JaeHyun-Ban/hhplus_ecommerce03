package com.hhplus.ecommerce.presentation.api.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 잔액 충전 요청 DTO
 *
 * Use Case: UC-001
 * - 잔액 충전 시 클라이언트가 전송하는 데이터
 */
@Schema(description = "잔액 충전 요청")
@Getter
@NoArgsConstructor
public class ChargeBalanceRequest {

    /**
     * 충전 금액
     *
     * 제약사항:
     * - 필수 입력
     * - 최소 1원 이상
     * - 0 이하 불가
     */
    @Schema(description = "충전 금액 (최소 1원)", example = "10000", required = true)
    @NotNull(message = "충전 금액은 필수입니다")
    @DecimalMin(value = "1", message = "충전 금액은 최소 1원 이상이어야 합니다")
    private BigDecimal amount;
}

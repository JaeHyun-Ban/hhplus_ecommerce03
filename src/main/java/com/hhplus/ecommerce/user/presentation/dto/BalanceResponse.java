package com.hhplus.ecommerce.user.presentation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 잔액 응답 DTO
 *
 * Use Case: UC-001, UC-002
 * - 잔액 충전/조회 결과
 */
@Schema(description = "잔액 응답")
@Getter
@Builder
public class BalanceResponse {

    @Schema(description = "사용자 ID", example = "1")
    private Long userId;

    @Schema(description = "현재 잔액", example = "50000")
    private BigDecimal balance;
}

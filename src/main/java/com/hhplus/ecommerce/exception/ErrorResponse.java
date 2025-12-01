package com.hhplus.ecommerce.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 에러 응답 DTO
 *
 * 모든 에러 응답의 표준 형식
 *
 * 구조:
 * - code: 에러 코드 (대문자 스네이크 케이스)
 * - message: 사용자에게 표시할 메시지
 * - fieldErrors: 필드별 검증 오류 (선택)
 * - timestamp: 발생 시각
 */
@Schema(description = "에러 응답")
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * 에러 코드
     *
     * 종류:
     * - VALIDATION_FAILED: 입력 검증 실패
     * - INVALID_REQUEST: 잘못된 요청
     * - CONFLICT: 상태 충돌 (재고 부족, 잔액 부족 등)
     * - CONCURRENT_MODIFICATION: 동시성 충돌
     * - INTERNAL_SERVER_ERROR: 서버 오류
     */
    @Schema(description = "에러 코드", example = "INVALID_REQUEST")
    private String code;

    /**
     * 에러 메시지
     *
     * 사용자에게 표시할 친화적인 메시지
     */
    @Schema(description = "에러 메시지", example = "상품을 찾을 수 없습니다")
    private String message;

    /**
     * 필드별 검증 오류
     *
     * Bean Validation 실패 시에만 포함
     * Key: 필드명, Value: 오류 메시지
     */
    @Schema(description = "필드별 오류 메시지 (검증 실패 시)")
    private Map<String, String> fieldErrors;

    /**
     * 에러 발생 시각
     */
    @Schema(description = "에러 발생 시각", example = "2025-11-05T10:30:00")
    private LocalDateTime timestamp;
}

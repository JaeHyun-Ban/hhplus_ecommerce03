package com.hhplus.ecommerce.presentation.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 처리 핸들러
 *
 * Presentation Layer - 예외 처리 계층
 *
 * 책임:
 * - 모든 예외를 일관된 형식으로 변환
 * - HTTP 상태 코드 매핑
 * - 에러 로깅
 *
 * Use Cases (예외 처리):
 * - UC-012 Extensions: 재고 부족, 잔액 부족, 중복 결제 등
 * - UC-015 Extensions: 취소 불가 상태 등
 * - Bean Validation 실패
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Bean Validation 실패 (400 Bad Request)
     *
     * 발생 상황:
     * - @Valid 검증 실패
     * - @NotNull, @Min, @NotBlank 등 제약 위반
     *
     * @param ex MethodArgumentNotValidException
     * @return 에러 응답 (필드별 오류 메시지)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();

        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        log.warn("입력 검증 실패: {}", fieldErrors);

        ErrorResponse response = ErrorResponse.builder()
            .code("VALIDATION_FAILED")
            .message("입력값이 올바르지 않습니다")
            .fieldErrors(fieldErrors)
            .timestamp(LocalDateTime.now())
            .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * IllegalArgumentException (400 Bad Request)
     *
     * 발생 상황:
     * - 잘못된 요청 파라미터
     * - 엔티티를 찾을 수 없음
     * - 비즈니스 규칙 위반
     *
     * @param ex IllegalArgumentException
     * @return 에러 응답
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex) {

        log.warn("잘못된 요청: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
            .code("INVALID_REQUEST")
            .message(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * IllegalStateException (409 Conflict 또는 410 Gone)
     *
     * 발생 상황:
     * - UC-012: 재고 부족, 잔액 부족
     * - UC-015: 취소 불가 상태
     * - UC-017: 쿠폰 소진, 발급 기간 아님, 1인당 발급 제한 초과
     * - 중복 결제 시도
     *
     * 쿠폰 소진 시 410 Gone:
     * - "쿠폰이 모두 소진되었습니다" → 410 Gone
     * - 영구적으로 불가능한 상태 (재시도 불필요)
     *
     * @param ex IllegalStateException
     * @return 에러 응답
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex) {

        log.warn("상태 충돌: {}", ex.getMessage());

        // 쿠폰 소진 케이스는 410 Gone 응답
        if (ex.getMessage().contains("소진") || ex.getMessage().contains("마감")) {
            ErrorResponse response = ErrorResponse.builder()
                .code("COUPON_SOLD_OUT")
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

            return ResponseEntity.status(HttpStatus.GONE).body(response);
        }

        // 기타 상태 충돌은 409 Conflict
        ErrorResponse response = ErrorResponse.builder()
            .code("CONFLICT")
            .message(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * OptimisticLockException (409 Conflict)
     *
     * 발생 상황:
     * - UC-012: 재고 차감 시 동시성 충돌 (낙관적 락)
     * - UC-017: 쿠폰 발급 시 동시성 충돌
     *
     * 처리:
     * - @Retryable로 자동 재시도되나, 최종 실패 시 이 핸들러 호출
     * - 클라이언트에게 재시도 요청
     *
     * @param ex ObjectOptimisticLockingFailureException
     * @return 에러 응답
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockException(
            ObjectOptimisticLockingFailureException ex) {

        log.warn("동시성 충돌 (낙관적 락): {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
            .code("CONCURRENT_MODIFICATION")
            .message("다른 사용자가 동시에 요청하여 처리할 수 없습니다. 잠시 후 다시 시도해주세요.")
            .timestamp(LocalDateTime.now())
            .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * 예상치 못한 예외 (500 Internal Server Error)
     *
     * 발생 상황:
     * - 모든 예상치 못한 오류
     * - 시스템 오류
     *
     * 처리:
     * - 상세 오류는 로그에만 기록
     * - 클라이언트에게는 일반적인 메시지 반환 (보안)
     *
     * @param ex Exception
     * @return 에러 응답
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("예상치 못한 오류 발생", ex);

        ErrorResponse response = ErrorResponse.builder()
            .code("INTERNAL_SERVER_ERROR")
            .message("일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.")
            .timestamp(LocalDateTime.now())
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}

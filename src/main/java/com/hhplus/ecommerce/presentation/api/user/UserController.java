package com.hhplus.ecommerce.presentation.api.user;

import com.hhplus.ecommerce.application.user.BalanceService;
import com.hhplus.ecommerce.application.user.UserService;
import com.hhplus.ecommerce.domain.user.BalanceHistory;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.presentation.api.user.dto.BalanceResponse;
import com.hhplus.ecommerce.presentation.api.user.dto.ChargeBalanceRequest;
import com.hhplus.ecommerce.presentation.api.user.dto.RegisterUserRequest;
import com.hhplus.ecommerce.presentation.api.user.dto.UserResponse;
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
import java.math.BigDecimal;

/**
 * 사용자 API 컨트롤러
 *
 * Presentation Layer - HTTP 요청/응답 처리 계층
 *
 * 책임:
 * - HTTP 요청 수신 및 응답 반환
 * - DTO ↔ Entity 변환
 * - 입력 검증 (Bean Validation)
 *
 * Use Cases:
 * - UC-001: 잔액 충전
 * - UC-002: 사용자 등록
 * - UC-003: 사용자 조회
 * - UC-004: 잔액 조회
 * - UC-005: 잔액 이력 조회
 */
@Tag(name = "User API", description = "사용자 관련 API")
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final BalanceService balanceService;

    /**
     * 사용자 등록
     *
     * Use Case: UC-002
     * - POST /api/users
     *
     * @param request 사용자 등록 요청
     * @return 등록된 사용자 정보
     */
    @Operation(summary = "사용자 등록", description = "새로운 사용자를 등록합니다")
    @PostMapping
    public ResponseEntity<UserResponse> registerUser(@Valid @RequestBody RegisterUserRequest request) {
        log.info("[API] POST /api/users - email: {}", request.getEmail());

        User user = userService.registerUser(request.getEmail(), request.getPassword(), request.getName());
        UserResponse response = UserResponse.from(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 사용자 조회
     *
     * Use Case: UC-003
     * - GET /api/users/{userId}
     *
     * @param userId 사용자 ID
     * @return 사용자 정보
     */
    @Operation(summary = "사용자 조회", description = "사용자 정보를 조회합니다")
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long userId) {
        log.info("[API] GET /api/users/{}", userId);

        User user = userService.getUser(userId);
        UserResponse response = UserResponse.from(user);

        return ResponseEntity.ok(response);
    }

    /**
     * 잔액 충전
     *
     * Use Case: UC-001
     * - POST /api/users/{userId}/balance/charge
     *
     * @param userId 사용자 ID
     * @param request 충전 요청 (금액)
     * @return 충전 후 잔액 정보
     */
    @Operation(summary = "잔액 충전", description = "사용자의 잔액을 충전합니다")
    @PostMapping("/{userId}/balance/charge")
    public ResponseEntity<BalanceResponse> chargeBalance(
            @PathVariable Long userId,
            @Valid @RequestBody ChargeBalanceRequest request) {

        log.info("[API] POST /api/users/{}/balance/charge - amount: {}", userId, request.getAmount());

        // Application Layer 호출
        BigDecimal newBalance = balanceService.chargeBalance(userId, request.getAmount());

        // DTO 변환
        BalanceResponse response = BalanceResponse.builder()
            .userId(userId)
            .balance(newBalance)
            .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 잔액 조회
     *
     * Use Case: UC-002
     * - GET /api/users/{userId}/balance
     *
     * @param userId 사용자 ID
     * @return 현재 잔액 정보
     */
    @Operation(summary = "잔액 조회", description = "사용자의 현재 잔액을 조회합니다")
    @GetMapping("/{userId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable Long userId) {
        log.info("[API] GET /api/users/{}/balance", userId);

        BigDecimal balance = balanceService.getBalance(userId);

        BalanceResponse response = BalanceResponse.builder()
            .userId(userId)
            .balance(balance)
            .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 잔액 이력 조회
     *
     * Use Case: UC-002 (확장)
     * - GET /api/users/{userId}/balance/history
     *
     * @param userId 사용자 ID
     * @param pageable 페이징 정보
     * @return 잔액 이력 페이지
     */
    @Operation(summary = "잔액 이력 조회", description = "사용자의 잔액 변동 이력을 조회합니다")
    @GetMapping("/{userId}/balance/history")
    public ResponseEntity<Page<BalanceHistory>> getBalanceHistory(
            @PathVariable Long userId,
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("[API] GET /api/users/{}/balance/history", userId);

        Page<BalanceHistory> history = balanceService.getBalanceHistory(userId, pageable);

        return ResponseEntity.ok(history);
    }
}

package com.hhplus.ecommerce.coupon.presentation.api;

import com.hhplus.ecommerce.coupon.application.CouponService;
import com.hhplus.ecommerce.coupon.domain.Coupon;
import com.hhplus.ecommerce.coupon.domain.UserCoupon;
import com.hhplus.ecommerce.coupon.presentation.api.dto.IssueCouponRequest;
import com.hhplus.ecommerce.coupon.presentation.api.dto.CouponResponse;
import com.hhplus.ecommerce.coupon.presentation.api.dto.UserCouponResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 쿠폰 API Controller
 *
 * Presentation Layer - REST API 엔드포인트
 *
 * 책임:
 * - HTTP 요청 처리 및 응답 변환
 * - UC-017: 선착순 쿠폰 발급
 * - UC-018: 발급 가능한 쿠폰 목록 조회
 * - UC-019: 내 쿠폰 목록 조회
 * - Request DTO 검증
 * - Response DTO 변환
 *
 * 레이어 의존성:
 * - Application Layer: CouponService
 */
@Slf4j
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
@Tag(name = "Coupon", description = "쿠폰 API")
public class CouponController {

    private final CouponService couponService;

    /**
     * 선착순 쿠폰 발급
     *
     * Use Case: UC-017
     *
     * Endpoint: POST /api/coupons/{couponId}/issue
     *
     * Request Body:
     * {
     *   "userId": 1
     * }
     *
     * Success Response (200 OK):
     * {
     *   "userCouponId": 123,
     *   "couponId": 1,
     *   "couponName": "신규 회원 10% 할인",
     *   "discountType": "PERCENTAGE",
     *   "discountValue": 10,
     *   "status": "ISSUED",
     *   "issuedAt": "2025-11-06T12:30:00",
     *   "validFrom": "2025-11-01T00:00:00",
     *   "validUntil": "2025-12-31T23:59:59"
     * }
     *
     * Error Responses:
     * - 400 Bad Request: 유효하지 않은 요청
     * - 404 Not Found: 쿠폰 또는 사용자를 찾을 수 없음
     * - 409 Conflict: 동시성 충돌 (쿠폰 발급 요청 집중)
     * - 410 Gone: 쿠폰 소진
     *
     * @param couponId 쿠폰 ID
     * @param request 발급 요청 (userId)
     * @return 발급된 사용자 쿠폰 정보
     */
    @PostMapping("/{couponId}/issue")
    @Operation(summary = "선착순 쿠폰 발급", description = "선착순으로 쿠폰을 발급받습니다. 동시 요청 시 낙관적 락으로 정확한 수량 제어가 보장됩니다.")
    public ResponseEntity<UserCouponResponse> issueCoupon(
            @Parameter(description = "쿠폰 ID", required = true, example = "1")
            @PathVariable Long couponId,
            @Valid @RequestBody IssueCouponRequest request) {

        log.info("POST /api/coupons/{}/issue - userId: {}", couponId, request.getUserId());

        UserCoupon userCoupon = couponService.issueCoupon(request.getUserId(), couponId);
        UserCouponResponse response = UserCouponResponse.from(userCoupon);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * 발급 가능한 쿠폰 목록 조회
     *
     * Use Case: UC-018
     *
     * Endpoint: GET /api/coupons/available
     *
     * Success Response (200 OK):
     * [
     *   {
     *     "couponId": 1,
     *     "code": "WELCOME10",
     *     "name": "신규 회원 10% 할인",
     *     "description": "신규 회원 대상 10% 할인 쿠폰",
     *     "discountType": "PERCENTAGE",
     *     "discountValue": 10,
     *     "minimumOrderAmount": 10000,
     *     "maximumDiscountAmount": 5000,
     *     "totalQuantity": 100,
     *     "issuedQuantity": 45,
     *     "remainingQuantity": 55,
     *     "issueStartAt": "2025-11-01T00:00:00",
     *     "issueEndAt": "2025-11-30T23:59:59",
     *     "validFrom": "2025-11-01T00:00:00",
     *     "validUntil": "2025-12-31T23:59:59"
     *   }
     * ]
     *
     * @return 발급 가능한 쿠폰 목록
     */
    @GetMapping("/available")
    @Operation(summary = "발급 가능한 쿠폰 목록 조회", description = "현재 발급 가능한 모든 쿠폰 목록을 조회합니다.")
    public ResponseEntity<List<CouponResponse>> getAvailableCoupons() {
        log.info("GET /api/coupons/available");

        List<Coupon> coupons = couponService.getAvailableCoupons();
        List<CouponResponse> response = coupons.stream()
            .map(CouponResponse::from)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * 쿠폰 상세 조회
     *
     * Use Case: UC-018 (확장)
     *
     * Endpoint: GET /api/coupons/{couponId}
     *
     * @param couponId 쿠폰 ID
     * @return 쿠폰 상세 정보
     */
    @GetMapping("/{couponId}")
    @Operation(summary = "쿠폰 상세 조회", description = "특정 쿠폰의 상세 정보를 조회합니다.")
    public ResponseEntity<CouponResponse> getCoupon(
            @Parameter(description = "쿠폰 ID", required = true, example = "1")
            @PathVariable Long couponId) {

        log.info("GET /api/coupons/{}", couponId);

        Coupon coupon = couponService.getCoupon(couponId);
        CouponResponse response = CouponResponse.from(coupon);

        return ResponseEntity.ok(response);
    }

    /**
     * 내 쿠폰 목록 조회 (전체)
     *
     * Use Case: UC-019
     *
     * Endpoint: GET /api/users/{userId}/coupons
     *
     * @param userId 사용자 ID
     * @return 사용자의 모든 쿠폰 목록 (사용 완료, 만료 포함)
     */
    @GetMapping("/users/{userId}")
    @Operation(summary = "내 쿠폰 목록 조회", description = "사용자의 모든 쿠폰 목록을 조회합니다 (사용 완료, 만료 포함).")
    public ResponseEntity<List<UserCouponResponse>> getMyCoupons(
            @Parameter(description = "사용자 ID", required = true, example = "1")
            @PathVariable Long userId) {

        log.info("GET /api/users/{}/coupons", userId);

        List<UserCoupon> userCoupons = couponService.getMyCoupons(userId);
        List<UserCouponResponse> response = userCoupons.stream()
            .map(UserCouponResponse::from)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * 사용 가능한 내 쿠폰 목록 조회
     *
     * Use Case: UC-019 (확장)
     * - 주문 시 적용 가능한 쿠폰만 조회
     *
     * Endpoint: GET /api/users/{userId}/coupons/available
     *
     * @param userId 사용자 ID
     * @return 사용 가능한 쿠폰 목록
     */
    @GetMapping("/users/{userId}/available")
    @Operation(summary = "사용 가능한 내 쿠폰 목록 조회", description = "사용자의 사용 가능한 쿠폰 목록을 조회합니다 (주문 시 적용 가능).")
    public ResponseEntity<List<UserCouponResponse>> getAvailableMyCoupons(
            @Parameter(description = "사용자 ID", required = true, example = "1")
            @PathVariable Long userId) {

        log.info("GET /api/users/{}/coupons/available", userId);

        List<UserCoupon> userCoupons = couponService.getAvailableMyCoupons(userId);
        List<UserCouponResponse> response = userCoupons.stream()
            .map(UserCouponResponse::from)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}
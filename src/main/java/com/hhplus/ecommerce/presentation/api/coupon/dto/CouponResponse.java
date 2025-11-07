package com.hhplus.ecommerce.presentation.api.coupon.dto;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponStatus;
import com.hhplus.ecommerce.domain.coupon.CouponType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 쿠폰 응답 DTO
 *
 * Use Case: UC-018 (쿠폰 목록 조회)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "쿠폰 정보")
public class CouponResponse {

    @Schema(description = "쿠폰 ID", example = "1")
    private Long couponId;

    @Schema(description = "쿠폰 코드", example = "WELCOME10")
    private String code;

    @Schema(description = "쿠폰명", example = "신규 회원 10% 할인")
    private String name;

    @Schema(description = "쿠폰 설명", example = "신규 회원 대상 10% 할인 쿠폰")
    private String description;

    @Schema(description = "할인 타입", example = "PERCENTAGE", allowableValues = {"FIXED_AMOUNT", "PERCENTAGE"})
    private CouponType discountType;

    @Schema(description = "할인값 (정액: 금액, 정률: %)", example = "10")
    private BigDecimal discountValue;

    @Schema(description = "최소 주문 금액", example = "10000")
    private BigDecimal minimumOrderAmount;

    @Schema(description = "최대 할인 금액 (정률 쿠폰용)", example = "5000")
    private BigDecimal maximumDiscountAmount;

    @Schema(description = "총 발급 수량", example = "100")
    private Integer totalQuantity;

    @Schema(description = "발급된 수량", example = "45")
    private Integer issuedQuantity;

    @Schema(description = "남은 수량", example = "55")
    private Integer remainingQuantity;

    @Schema(description = "1인당 최대 발급 수", example = "1")
    private Integer maxIssuePerUser;

    @Schema(description = "발급 시작 시간", example = "2025-11-01T00:00:00")
    private LocalDateTime issueStartAt;

    @Schema(description = "발급 종료 시간", example = "2025-11-30T23:59:59")
    private LocalDateTime issueEndAt;

    @Schema(description = "사용 시작 시간", example = "2025-11-01T00:00:00")
    private LocalDateTime validFrom;

    @Schema(description = "사용 종료 시간", example = "2025-12-31T23:59:59")
    private LocalDateTime validUntil;

    @Schema(description = "쿠폰 상태", example = "ACTIVE", allowableValues = {"ACTIVE", "INACTIVE", "EXHAUSTED"})
    private CouponStatus status;

    /**
     * Entity → DTO 변환
     */
    public static CouponResponse from(Coupon coupon) {
        return CouponResponse.builder()
            .couponId(coupon.getId())
            .code(coupon.getCode())
            .name(coupon.getName())
            .description(coupon.getDescription())
            .discountType(coupon.getType())
            .discountValue(coupon.getDiscountValue())
            .minimumOrderAmount(coupon.getMinimumOrderAmount())
            .maximumDiscountAmount(coupon.getMaximumDiscountAmount())
            .totalQuantity(coupon.getTotalQuantity())
            .issuedQuantity(coupon.getIssuedQuantity())
            .remainingQuantity(coupon.getTotalQuantity() - coupon.getIssuedQuantity())
            .maxIssuePerUser(coupon.getMaxIssuePerUser())
            .issueStartAt(coupon.getIssueStartAt())
            .issueEndAt(coupon.getIssueEndAt())
            .validFrom(coupon.getValidFrom())
            .validUntil(coupon.getValidUntil())
            .status(coupon.getStatus())
            .build();
    }
}

package com.hhplus.ecommerce.presentation.api.coupon.dto;

import com.hhplus.ecommerce.domain.coupon.CouponType;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 사용자 쿠폰 응답 DTO
 *
 * Use Case: UC-017 (쿠폰 발급), UC-019 (내 쿠폰 조회)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "사용자 쿠폰 정보")
public class UserCouponResponse {

    @Schema(description = "사용자 쿠폰 ID", example = "123")
    private Long userCouponId;

    @Schema(description = "쿠폰 ID", example = "1")
    private Long couponId;

    @Schema(description = "쿠폰 코드", example = "WELCOME10")
    private String couponCode;

    @Schema(description = "쿠폰명", example = "신규 회원 10% 할인")
    private String couponName;

    @Schema(description = "쿠폰 설명", example = "신규 회원 대상 10% 할인 쿠폰")
    private String couponDescription;

    @Schema(description = "할인 타입", example = "PERCENTAGE", allowableValues = {"FIXED_AMOUNT", "PERCENTAGE"})
    private CouponType discountType;

    @Schema(description = "할인값 (정액: 금액, 정률: %)", example = "10")
    private BigDecimal discountValue;

    @Schema(description = "최소 주문 금액", example = "10000")
    private BigDecimal minimumOrderAmount;

    @Schema(description = "최대 할인 금액", example = "5000")
    private BigDecimal maximumDiscountAmount;

    @Schema(description = "사용자 쿠폰 상태", example = "ISSUED", allowableValues = {"ISSUED", "USED", "EXPIRED", "REVOKED"})
    private UserCouponStatus status;

    @Schema(description = "발급 일시", example = "2025-11-06T12:30:00")
    private LocalDateTime issuedAt;

    @Schema(description = "사용 일시", example = "2025-11-10T15:20:00")
    private LocalDateTime usedAt;

    @Schema(description = "만료 일시", example = "2025-12-31T23:59:59")
    private LocalDateTime expiredAt;

    @Schema(description = "사용 시작 시간", example = "2025-11-01T00:00:00")
    private LocalDateTime validFrom;

    @Schema(description = "사용 종료 시간", example = "2025-12-31T23:59:59")
    private LocalDateTime validUntil;

    @Schema(description = "사용 가능 여부", example = "true")
    private Boolean canUse;

    /**
     * Entity → DTO 변환
     */
    public static UserCouponResponse from(UserCoupon userCoupon) {
        return UserCouponResponse.builder()
            .userCouponId(userCoupon.getId())
            .couponId(userCoupon.getCoupon().getId())
            .couponCode(userCoupon.getCoupon().getCode())
            .couponName(userCoupon.getCoupon().getName())
            .couponDescription(userCoupon.getCoupon().getDescription())
            .discountType(userCoupon.getCoupon().getType())
            .discountValue(userCoupon.getCoupon().getDiscountValue())
            .minimumOrderAmount(userCoupon.getCoupon().getMinimumOrderAmount())
            .maximumDiscountAmount(userCoupon.getCoupon().getMaximumDiscountAmount())
            .status(userCoupon.getStatus())
            .issuedAt(userCoupon.getIssuedAt())
            .usedAt(userCoupon.getUsedAt())
            .expiredAt(userCoupon.getExpiredAt())
            .validFrom(userCoupon.getCoupon().getValidFrom())
            .validUntil(userCoupon.getCoupon().getValidUntil())
            .canUse(userCoupon.canUse())
            .build();
    }
}

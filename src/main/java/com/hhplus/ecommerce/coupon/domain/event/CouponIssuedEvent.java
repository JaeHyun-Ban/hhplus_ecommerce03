package com.hhplus.ecommerce.coupon.domain.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

/**
 * 쿠폰 발급 완료 도메인 이벤트
 *
 * Redis에서 쿠폰 발급이 완료되었을 때 발행되는 이벤트
 * 이벤트를 통해 비동기로 DB에 쿠폰 발급 정보를 저장
 *
 * 이벤트 기반 아키텍처:
 * - Producer: CouponService (Redis 발급 성공 시 이벤트 발행)
 * - Consumer: CouponEventHandler (비동기로 DB 저장)
 *
 * 장점:
 * - Redis 성공 시 즉시 응답 (DB 저장 대기 불필요)
 * - DB Deadlock이 사용자 경험에 영향 없음
 * - 실패 시 자동 재시도 가능
 */
@Getter
@RequiredArgsConstructor
public class CouponIssuedEvent {

    /**
     * 쿠폰 ID
     */
    private final Long couponId;

    /**
     * 사용자 ID
     */
    private final Long userId;

    /**
     * Redis에서 발급된 순위 (1부터 시작)
     */
    private final Long rank;

    /**
     * 전체 발급 수량 (Redis 기준)
     */
    private final Long issuedCount;

    /**
     * 이벤트 발생 시각
     */
    private final LocalDateTime occurredAt;

    /**
     * 정적 팩토리 메서드
     */
    public static CouponIssuedEvent of(Long couponId, Long userId, Long rank, Long issuedCount) {
        return new CouponIssuedEvent(
            couponId,
            userId,
            rank,
            issuedCount,
            LocalDateTime.now()
        );
    }

    @Override
    public String toString() {
        return String.format("CouponIssuedEvent(couponId=%d, userId=%d, rank=%d, issuedCount=%d, occurredAt=%s)",
                             couponId, userId, rank, issuedCount, occurredAt);
    }
}

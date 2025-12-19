package com.hhplus.ecommerce.coupon.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 쿠폰 발급 완료 도메인 이벤트
 *
 * Redis에서 쿠폰 발급이 완료되었을 때 발행되는 이벤트
 * Kafka를 통해 비동기로 DB에 쿠폰 발급 정보를 저장
 *
 * Kafka 기반 아키텍처:
 * - Producer: CouponService (Redis 발급 성공 시 Kafka로 이벤트 발행)
 * - Consumer: CouponKafkaConsumer (Kafka 메시지 수신 후 비동기로 DB 저장)
 *
 * 장점:
 * - Redis 성공 시 즉시 응답 (DB 저장 대기 불필요)
 * - DB Deadlock이 사용자 경험에 영향 없음
 * - Kafka의 재시도 및 DLQ로 안정성 보장
 * - Consumer 수평 확장 가능
 */
@Getter
@NoArgsConstructor  // Kafka JSON 역직렬화를 위한 기본 생성자
@AllArgsConstructor  // 정적 팩토리 메서드에서 사용
public class CouponIssuedEvent {

    /**
     * 쿠폰 ID
     */
    private Long couponId;

    /**
     * 사용자 ID
     */
    private Long userId;

    /**
     * Redis에서 발급된 순위 (1부터 시작)
     */
    private Long rank;

    /**
     * 전체 발급 수량 (Redis 기준)
     */
    private Long issuedCount;

    /**
     * 이벤트 발생 시각
     */
    private LocalDateTime occurredAt;

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
        return new StringBuilder("CouponIssuedEvent(couponId=")
            .append(couponId)
            .append(", userId=")
            .append(userId)
            .append(", rank=")
            .append(rank)
            .append(", issuedCount=")
            .append(issuedCount)
            .append(", occurredAt=")
            .append(occurredAt)
            .append(")")
            .toString();
    }
}

package com.hhplus.ecommerce.integration.infrastructure.persistence;

import com.hhplus.ecommerce.integration.domain.EventStatus;
import com.hhplus.ecommerce.integration.domain.EventType;
import com.hhplus.ecommerce.integration.domain.OutboundEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 외부 전송 이벤트 Repository
 *
 * Infrastructure Layer - 데이터베이스 접근 계층
 *
 * 책임:
 * - 외부 시스템 연동 이벤트 관리
 * - 재시도 대상 이벤트 조회
 * - 실패 이벤트 추적
 *
 * Use Cases:
 * - UC-012: 주문 생성 시 이벤트 발행
 * - UC-015: 주문 취소 시 이벤트 발행
 * - 배치 작업: 실패 이벤트 재시도
 */
@Repository
public interface OutboundEventRepository extends JpaRepository<OutboundEvent, Long> {

    /**
     * 재시도 대상 이벤트 조회
     *
     * Use Case:
     * - 배치 작업: 실패 이벤트 재시도
     *
     * 재시도 조건:
     * - status = PENDING 또는 FAILED
     * - nextRetryAt <= NOW (재시도 시각 도래)
     * - retryCount < maxRetryCount (재시도 횟수 제한 내)
     *
     * 성능 최적화:
     * - idx_outbound_events_status_retry 복합 인덱스 사용
     *
     * @param now 현재 시각
     * @return 재시도 대상 이벤트 목록
     */
    @Query("SELECT oe FROM OutboundEvent oe " +
           "WHERE oe.status IN ('PENDING', 'FAILED') " +
           "AND oe.nextRetryAt <= :now " +
           "AND oe.retryCount < oe.maxRetryCount " +
           "ORDER BY oe.nextRetryAt ASC")
    List<OutboundEvent> findEventsToRetry(@Param("now") LocalDateTime now);

    /**
     * Dead Letter Queue 이벤트 조회
     *
     * Use Case:
     * - 관리자 기능: 최종 실패 이벤트 모니터링
     *
     * Dead Letter:
     * - status = DEAD_LETTER
     * - 최대 재시도 횟수 초과로 더 이상 재시도 불가
     * - 수동 처리 또는 보상 트랜잭션 필요
     *
     * @return Dead Letter 이벤트 목록
     */
    @Query("SELECT oe FROM OutboundEvent oe " +
           "WHERE oe.status = 'DEAD_LETTER' " +
           "ORDER BY oe.createdAt DESC")
    List<OutboundEvent> findDeadLetterEvents();

    /**
     * 특정 엔티티 타입 + ID로 이벤트 조회
     *
     * Use Case:
     * - 관리자 기능: 특정 주문의 이벤트 이력 조회
     *
     * @param eventType 이벤트 타입 (예: ORDER_CREATED)
     * @param entityId 엔티티 ID (예: 주문 ID)
     * @return 이벤트 목록
     */
    @Query("SELECT oe FROM OutboundEvent oe " +
           "WHERE oe.eventType = :eventType " +
           "AND oe.entityId = :entityId " +
           "ORDER BY oe.createdAt DESC")
    List<OutboundEvent> findByEventTypeAndEntityId(
        @Param("eventType") EventType eventType,
        @Param("entityId") Long entityId
    );

    /**
     * 특정 상태의 이벤트 목록 조회
     *
     * Use Case:
     * - 모니터링: 상태별 이벤트 통계
     *
     * @param status 이벤트 상태
     * @return 이벤트 목록
     */
    List<OutboundEvent> findByStatus(EventStatus status);

    /**
     * 오래된 성공 이벤트 조회
     *
     * Use Case:
     * - 배치 작업: 이벤트 정리 (보관 기간 경과)
     *
     * @param beforeDate 기준 날짜 (예: 30일 이전)
     * @return 오래된 이벤트 목록
     */
    @Query("SELECT oe FROM OutboundEvent oe " +
           "WHERE oe.status = 'SUCCESS' " +
           "AND oe.completedAt < :beforeDate")
    List<OutboundEvent> findOldSuccessEvents(@Param("beforeDate") LocalDateTime beforeDate);
}

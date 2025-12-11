package com.hhplus.ecommerce.common.infrastructure;

import com.hhplus.ecommerce.common.domain.DomainEventStore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 도메인 이벤트 스토어 Repository
 *
 * Infrastructure Layer - 데이터 접근 계층
 *
 * 책임:
 * - 도메인 이벤트 CRUD
 * - 재시도 대상 조회
 * - 이벤트 히스토리 조회
 */
@Repository
public interface DomainEventStoreRepository extends JpaRepository<DomainEventStore, Long> {

    /**
     * 재시도 가능한 이벤트 조회
     *
     * 조건:
     * - 상태가 PENDING
     * - 재시도 횟수 < 최대 재시도 횟수
     * - 다음 재시도 시각 <= 현재 시각
     *
     * @param now 현재 시각
     * @param limit 조회 제한 수
     * @return 재시도 대상 이벤트 목록
     */
    @Query("SELECT e FROM DomainEventStore e " +
           "WHERE e.status = 'PENDING' " +
           "AND e.retryCount < e.maxRetryCount " +
           "AND e.nextRetryAt <= :now " +
           "ORDER BY e.nextRetryAt ASC " +
           "LIMIT :limit")
    List<DomainEventStore> findRetryableEvents(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 특정 타입의 재시도 가능한 이벤트 조회
     *
     * @param eventType 이벤트 타입
     * @param now 현재 시각
     * @param limit 조회 제한 수
     * @return 재시도 대상 이벤트 목록
     */
    @Query("SELECT e FROM DomainEventStore e " +
           "WHERE e.eventType = :eventType " +
           "AND e.status = 'PENDING' " +
           "AND e.retryCount < e.maxRetryCount " +
           "AND e.nextRetryAt <= :now " +
           "ORDER BY e.nextRetryAt ASC " +
           "LIMIT :limit")
    List<DomainEventStore> findRetryableEventsByType(
        @Param("eventType") DomainEventStore.EventType eventType,
        @Param("now") LocalDateTime now,
        @Param("limit") int limit
    );

    /**
     * Aggregate ID와 이벤트 타입으로 조회 (중복 방지용)
     *
     * @param eventType 이벤트 타입
     * @param aggregateId Aggregate ID
     * @return 이벤트 (있으면)
     */
    Optional<DomainEventStore> findByEventTypeAndAggregateId(
        DomainEventStore.EventType eventType,
        Long aggregateId
    );

    /**
     * 특정 상태의 이벤트 조회
     *
     * @param status 이벤트 상태
     * @return 이벤트 목록
     */
    List<DomainEventStore> findByStatus(DomainEventStore.EventStatus status);

    /**
     * 특정 타입과 상태의 이벤트 조회
     *
     * @param eventType 이벤트 타입
     * @param status 이벤트 상태
     * @return 이벤트 목록
     */
    List<DomainEventStore> findByEventTypeAndStatus(
        DomainEventStore.EventType eventType,
        DomainEventStore.EventStatus status
    );

    /**
     * Aggregate ID로 이벤트 히스토리 조회
     *
     * @param aggregateId Aggregate ID
     * @return 이벤트 히스토리 (생성 시각 순)
     */
    @Query("SELECT e FROM DomainEventStore e " +
           "WHERE e.aggregateId = :aggregateId " +
           "ORDER BY e.createdAt ASC")
    List<DomainEventStore> findEventHistoryByAggregateId(@Param("aggregateId") Long aggregateId);

    /**
     * Aggregate ID와 타입으로 이벤트 히스토리 조회
     *
     * @param aggregateId Aggregate ID
     * @param aggregateType Aggregate Type
     * @return 이벤트 히스토리 (생성 시각 순)
     */
    @Query("SELECT e FROM DomainEventStore e " +
           "WHERE e.aggregateId = :aggregateId " +
           "AND e.aggregateType = :aggregateType " +
           "ORDER BY e.createdAt ASC")
    List<DomainEventStore> findEventHistoryByAggregateIdAndType(
        @Param("aggregateId") Long aggregateId,
        @Param("aggregateType") String aggregateType
    );

    /**
     * 기간별 이벤트 조회
     *
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 이벤트 목록
     */
    @Query("SELECT e FROM DomainEventStore e " +
           "WHERE e.createdAt >= :startDate " +
           "AND e.createdAt <= :endDate " +
           "ORDER BY e.createdAt DESC")
    List<DomainEventStore> findEventsByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
}

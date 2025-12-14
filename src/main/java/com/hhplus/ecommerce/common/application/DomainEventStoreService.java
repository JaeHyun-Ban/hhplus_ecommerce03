package com.hhplus.ecommerce.common.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.ecommerce.common.domain.DomainEventStore;
import com.hhplus.ecommerce.common.domain.event.EventPayload;
import com.hhplus.ecommerce.common.infrastructure.DomainEventStoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 도메인 이벤트 스토어 서비스
 *
 * Application Layer - 서비스
 *
 * 책임:
 * - 도메인 이벤트 저장 및 조회
 * - 이벤트 페이로드 JSON 직렬화/역직렬화
 * - 이벤트 중복 저장 방지
 *
 * Use Cases:
 * - 모든 도메인 이벤트의 이벤트 소싱
 * - 이벤트 처리 실패 시 보상 트랜잭션
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DomainEventStoreService {

    private final DomainEventStoreRepository eventStoreRepository;
    private final ObjectMapper objectMapper;

    /**
     * 도메인 이벤트 저장 (보상 트랜잭션)
     *
     * 독립적인 트랜잭션으로 실행:
     * - REQUIRES_NEW로 현재 트랜잭션과 분리
     * - 이벤트 저장 실패 시에도 로그만 기록
     *
     * @param eventType 이벤트 타입
     * @param aggregateId Aggregate ID
     * @param aggregateType Aggregate Type
     * @param payload 이벤트 페이로드
     * @return 저장된 이벤트
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<DomainEventStore> saveEvent(
        DomainEventStore.EventType eventType,
        Long aggregateId,
        String aggregateType,
        EventPayload payload
    ) {
        try {
            // 중복 저장 방지: 이미 저장된 이벤트가 있는지 확인
            Optional<DomainEventStore> existing = eventStoreRepository
                .findByEventTypeAndAggregateId(eventType, aggregateId);

            if (existing.isPresent()) {
                log.warn("[이벤트 스토어] 이미 저장된 이벤트 - eventType: {}, aggregateId: {}",
                         eventType, aggregateId);
                return existing;
            }

            // 페이로드 JSON 직렬화
            String payloadJson = objectMapper.writeValueAsString(payload);

            // 이벤트 생성 및 저장
            DomainEventStore event = DomainEventStore.builder()
                .eventType(eventType)
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .payload(payloadJson)
                .build();

            DomainEventStore savedEvent = eventStoreRepository.save(event);

            log.info("[이벤트 스토어] 이벤트 저장 완료 - eventType: {}, aggregateId: {}, eventId: {}",
                     eventType, aggregateId, savedEvent.getId());

            return Optional.of(savedEvent);

        } catch (JsonProcessingException e) {
            log.error("[이벤트 스토어] JSON 직렬화 실패 - eventType: {}, aggregateId: {}",
                      eventType, aggregateId, e);
            return Optional.empty();

        } catch (Exception e) {
            log.error("[이벤트 스토어] 이벤트 저장 중 예외 발생 - eventType: {}, aggregateId: {}",
                      eventType, aggregateId, e);
            return Optional.empty();
        }
    }

    /**
     * 이벤트 페이로드 역직렬화
     *
     * @param event 이벤트
     * @param payloadClass 페이로드 클래스
     * @return 역직렬화된 페이로드
     */
    public <T extends EventPayload> Optional<T> deserializePayload(
        DomainEventStore event,
        Class<T> payloadClass
    ) {
        try {
            T payload = objectMapper.readValue(event.getPayload(), payloadClass);
            return Optional.of(payload);

        } catch (JsonProcessingException e) {
            log.error("[이벤트 스토어] JSON 역직렬화 실패 - eventId: {}, eventType: {}",
                      event.getId(), event.getEventType(), e);
            return Optional.empty();
        }
    }

    /**
     * 이벤트 조회
     *
     * @param eventId 이벤트 ID
     * @return 이벤트
     */
    @Transactional(readOnly = true)
    public Optional<DomainEventStore> findEvent(Long eventId) {
        return eventStoreRepository.findById(eventId);
    }
}

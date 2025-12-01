package com.hhplus.ecommerce.order.application;

import com.hhplus.ecommerce.order.domain.OrderSequence;
import com.hhplus.ecommerce.order.infrastructure.persistence.OrderSequenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 주문 번호 시퀀스 서비스
 *
 * 주문 번호 생성을 위한 시퀀스 관리 전용 서비스
 * - REQUIRES_NEW 트랜잭션으로 시퀀스 증가를 즉시 커밋
 * - 다른 트랜잭션과 독립적으로 동작하여 동시성 문제 해결
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSequenceService {

    private final OrderSequenceRepository orderSequenceRepository;

    /**
     * 주문 번호 생성 (동시성 안전)
     *
     * 동시성 제어:
     * - REQUIRES_NEW: 별도 트랜잭션으로 즉시 커밋
     * - PESSIMISTIC_WRITE: 비관적 락으로 동시 읽기 방지
     * - 시퀀스 증가 후 즉시 커밋되므로 다른 트랜잭션에서 최신 값 조회 가능
     *
     * @return 생성된 주문 번호
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateOrderNumber() {
        LocalDate today = LocalDate.now();
        String todayStr = today.toString();

        // 비관적 락으로 시퀀스 조회
        OrderSequence sequence = orderSequenceRepository.findByDateWithLock(todayStr)
                .orElseGet(() -> {
                    // 첫 주문: 새 시퀀스 생성
                    OrderSequence newSequence = OrderSequence.create(today);
                    return orderSequenceRepository.save(newSequence);
                });

        // 시퀀스 증가
        sequence.incrementAndGet();
        orderSequenceRepository.save(sequence);

        // 주문 번호 생성
        String orderNumber = sequence.generateOrderNumber();

        log.debug("주문 번호 생성 완료 - orderNumber: {}, sequence: {}, thread: {}",
                  orderNumber, sequence.getSequence(), Thread.currentThread().getName());

        return orderNumber;
    }
}

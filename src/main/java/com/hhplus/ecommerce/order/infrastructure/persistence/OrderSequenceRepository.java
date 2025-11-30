package com.hhplus.ecommerce.order.infrastructure.persistence;

import com.hhplus.ecommerce.order.domain.OrderSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 주문 번호 시퀀스 Repository
 *
 * Infrastructure Layer - 데이터베이스 접근 계층
 *
 * 책임:
 * - OrderSequence 엔티티 CRUD 연산
 * - 비관적 락을 이용한 동시성 제어 (시퀀스 증가 시)
 *
 * 동시성 전략:
 * - PESSIMISTIC_WRITE: SELECT ... FOR UPDATE
 * - 같은 날짜의 시퀀스를 동시에 읽으려는 트랜잭션은 대기
 * - 시퀀스 증가가 원자적으로(atomic) 수행됨
 */
@Repository
public interface OrderSequenceRepository extends JpaRepository<OrderSequence, String> {

    /**
     * 날짜로 시퀀스 조회 (비관적 락)
     *
     * 동시성 제어:
     * - 여러 트랜잭션이 동시에 같은 날짜의 시퀀스를 조회하려 할 때
     * - SELECT ... FOR UPDATE로 한 트랜잭션만 읽기 가능
     * - 다른 트랜잭션들은 대기 → 순차적으로 시퀀스 증가
     *
     * 시나리오 (100명이 동시에 주문):
     * T1: SELECT * FROM order_sequences WHERE date='2025-11-20' FOR UPDATE
     *     → sequence=0 읽음 → sequence++ → UPDATE sequence=1 → COMMIT
     * T2: [대기] → sequence=1 읽음 → sequence++ → UPDATE sequence=2 → COMMIT
     * T3: [대기] → sequence=2 읽음 → sequence++ → UPDATE sequence=3 → COMMIT
     * ...
     * T100: sequence=99 읽음 → sequence++ → UPDATE sequence=100 → COMMIT
     *
     * 결과: 중복 없이 1~100까지 순차적 생성
     *
     * @param date 주문 날짜 (yyyy-MM-dd)
     * @return OrderSequence (Optional)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM OrderSequence s WHERE s.date = :date")
    Optional<OrderSequence> findByDateWithLock(@Param("date") String date);
}
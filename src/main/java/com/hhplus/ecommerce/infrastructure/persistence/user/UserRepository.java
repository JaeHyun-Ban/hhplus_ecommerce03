package com.hhplus.ecommerce.infrastructure.persistence.user;

import com.hhplus.ecommerce.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.Optional;

/**
 * 사용자 Repository
 *
 * Infrastructure Layer - 데이터베이스 접근 계층
 *
 * 책임:
 * - 사용자 엔티티 CRUD 연산
 * - 비관적 락을 이용한 동시성 제어 (잔액 차감 시)
 *
 * Use Cases:
 * - UC-001: 잔액 충전
 * - UC-002: 잔액 조회
 * - UC-012: 주문 생성 (잔액 차감)
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 사용자 ID로 조회 (비관적 락)
     *
     * Use Case:
     * - UC-001: 잔액 충전 시 동시성 제어
     * - UC-012: 주문 결제 시 잔액 차감 동시성 제어
     *
     * 락 전략:
     * - PESSIMISTIC_WRITE: SELECT ... FOR UPDATE
     * - 다른 트랜잭션이 읽기/쓰기 모두 대기하도록 함
     * - 잔액 차감 시 정확한 동시성 보장
     *
     * @param id 사용자 ID
     * @return 사용자 엔티티 (Optional)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithLock(@Param("id") Long id);

    /**
     * 이메일로 사용자 조회
     *
     * Use Case:
     * - 회원가입 시 이메일 중복 체크
     * - 로그인 시 사용자 조회
     *
     * @param email 사용자 이메일
     * @return 사용자 엔티티 (Optional)
     */
    Optional<User> findByEmail(String email);

    /**
     * 이메일 존재 여부 확인
     *
     * Use Case:
     * - 회원가입 시 이메일 중복 체크 최적화
     *
     * @param email 이메일
     * @return 존재 여부
     */
    boolean existsByEmail(String email);
}

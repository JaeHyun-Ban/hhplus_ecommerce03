# 동시성 문제 해결 보고서

## 목차
1. [문제 식별](#1-문제-식별)
2. [분석](#2-분석)
3. [해결 방안](#3-해결-방안)
4. [구현 내역](#4-구현-내역)
5. [테스트 결과](#5-테스트-결과)
6. [결론](#6-결론)

---

## 1. 문제 식별

이커머스 서비스에서 발생하는 주요 동시성 문제는 다음 3가지로 분류됩니다:

### 1.1 재고 차감 동시성 문제

**시나리오**
- 재고 10개 상품에 100명이 동시에 주문 시도
- 동시성 제어가 없을 경우: 100개 주문이 모두 성공 (재고 -90개 오류)
- 기대 결과: 정확히 10명만 성공, 90명 실패

**영향도**: 🔴 Critical
- 재고 부족으로 배송 불가 → 고객 불만
- 재무 손실 발생

### 1.2 잔액 충전/차감 동시성 문제

**시나리오 A: 동시 충전**
- 동일 계정에 100개의 충전 요청(각 1,000원)이 동시에 발생
- 동시성 제어가 없을 경우: Lost Update 발생 (일부 충전 누락)
- 기대 결과: 정확히 100,000원 충전

**시나리오 B: 충전과 차감 동시 발생**
- 충전 요청 50회 + 주문(차감) 요청 50회 동시 실행
- 동시성 제어가 없을 경우: 데이터 정합성 오류
- 기대 결과: 모든 트랜잭션 정확히 반영

**영향도**: 🔴 Critical
- 금전 거래 오류 → 신뢰도 하락
- 법적 문제 발생 가능

### 1.3 선착순 쿠폰 발급 동시성 문제

**시나리오**
- 선착순 100개 쿠폰에 1,000명이 동시에 발급 요청
- 동시성 제어가 없을 경우: 100개 이상 발급 (오버커밋)
- 기대 결과: 정확히 100명만 발급 성공

**영향도**: 🟠 High
- 마케팅 비용 초과 발생
- 프로모션 신뢰도 하락

---

## 2. 분석

### 2.1 동시성 문제 발생 원인

모든 문제의 근본 원인은 **Race Condition**입니다:

```
시간 ---->
Thread A: [READ stock=10] -----------> [UPDATE stock=9]
Thread B: [READ stock=10] --> [UPDATE stock=9]
                                       ^^^^
                                    동일한 값으로 업데이트
                                    (1개가 아닌 2개 차감되어야 함)
```

### 2.2 문제별 특성 분석

#### 재고 차감 문제

**특성**
- 읽기(조회) 빈도: 높음
- 쓰기(차감) 빈도: 중간
- 충돌 확률: 중간 (인기 상품의 경우 높음)
- 데이터 크기: 작음 (단일 컬럼)

**요구사항**
- 정확한 재고 수량 보장
- 높은 처리량(throughput)
- 재고 소진 시 즉시 주문 차단

#### 잔액 문제

**특성**
- 읽기(조회) 빈도: 매우 높음
- 쓰기(충전/차감) 빈도: 높음
- 충돌 확률: 매우 높음 (사용자당 단일 계정)
- 데이터 크기: 작음 (단일 컬럼)

**요구사항**
- 절대적인 정확성 보장 (금전 거래)
- 데드락 방지
- 충전과 차감 동시 처리 지원

#### 쿠폰 발급 문제

**특성**
- 읽기(조회) 빈도: 높음
- 쓰기(발급) 빈도: 매우 높음 (이벤트 시작 시)
- 충돌 확률: 매우 높음 (선착순 특성)
- 데이터 크기: 작음 (단일 레코드)

**요구사항**
- 정확한 선착순 보장
- 높은 동시 요청 처리
- 중복 발급 방지

---

## 3. 해결 방안

### 3.1 DB 기반 동시성 제어 방법 비교

| 구분 | 낙관적 락 (Optimistic Lock) | 비관적 락 (Pessimistic Lock) |
|------|---------------------------|----------------------------|
| **원리** | Version 기반 충돌 감지 | DB Row Lock |
| **충돌 시** | 예외 발생 → 재시도 | 대기 → 순차 처리 |
| **성능** | 충돌 적을 때 우수 | 충돌 많을 때 안정적 |
| **사용 시점** | 읽기 많고 쓰기 적을 때 | 쓰기 많고 데이터 정확성 중요 |
| **SQL** | `SELECT + UPDATE WHERE version=?` | `SELECT FOR UPDATE` |

### 3.2 문제별 해결 방안 선정

#### 3.2.1 재고 차감: 낙관적 락 (Optimistic Lock)

**선정 근거**
1. 대부분의 상품은 재고가 충분 → 충돌 확률 낮음
2. 읽기(조회) 요청이 쓰기(주문)보다 훨씬 많음
3. 높은 처리량 필요 (많은 사용자 동시 조회)

**구현 방식**
- JPA `@Version` 어노테이션 사용
- `LockModeType.OPTIMISTIC` 적용
- 충돌 시 최대 5회 재시도 (`@Retryable`)

**SQL 예시**
```sql
-- 1. 조회 (version 포함)
SELECT id, stock, version FROM products WHERE id = 1;
-- 결과: stock=10, version=0

-- 2. 재고 차감 (version 검증)
UPDATE products
SET stock = 9, version = 1
WHERE id = 1 AND version = 0;
-- 다른 트랜잭션이 먼저 업데이트했다면 affected rows = 0
-- → OptimisticLockException 발생 → 재시도
```

**장점**
- 락 대기 시간 없음 → 높은 처리량
- DB 리소스 효율적
- 읽기 성능 우수

**단점**
- 충돌 시 재시도 필요
- 재시도 실패 시 사용자 경험 저하 가능

#### 3.2.2 잔액 충전/차감: 비관적 락 (Pessimistic Lock)

**선정 근거**
1. 금전 거래 → 절대적 정확성 필요
2. 충돌 확률 매우 높음 (사용자당 단일 계정)
3. 재시도로 인한 오버헤드 방지

**구현 방식**
- `LockModeType.PESSIMISTIC_WRITE` 적용
- `SELECT FOR UPDATE` 사용
- 트랜잭션 격리 수준: READ_COMMITTED

**SQL 예시**
```sql
-- 1. 조회 및 락 획득 (다른 트랜잭션 대기)
SELECT id, balance FROM users WHERE id = 1 FOR UPDATE;
-- 결과: balance=10000 (락 획득, 다른 트랜잭션은 대기)

-- 2. 잔액 충전
UPDATE users SET balance = 11000 WHERE id = 1;

-- 3. 커밋 시 락 해제
COMMIT;
```

**장점**
- 데이터 정합성 100% 보장
- 재시도 불필요
- 구현 단순

**단점**
- 락 대기 시간 발생
- 처리량 감소
- 데드락 가능성 (다중 리소스 락)

**데드락 방지 전략**
- 락 획득 순서 고정 (User → Product 순)
- 락 타임아웃 설정 (10초)
- 트랜잭션 최소화

#### 3.2.3 선착순 쿠폰 발급: 낙관적 락 (Optimistic Lock)

**선정 근거**
1. 순간적으로 많은 요청 발생 → 비관적 락 시 성능 저하
2. 발급 실패 시 사용자가 이해 가능 (선착순 특성)
3. 재시도로 대부분 성공 가능

**구현 방식**
- JPA `@Version` 어노테이이션 사용
- 최대 5회 재시도
- 발급 수량 실시간 체크

**SQL 예시**
```sql
-- 1. 조회
SELECT id, issued_quantity, total_quantity, version
FROM coupons WHERE id = 1;
-- 결과: issued_quantity=99, total_quantity=100, version=15

-- 2. 발급 (version 검증)
UPDATE coupons
SET issued_quantity = 100, version = 16
WHERE id = 1 AND version = 15 AND issued_quantity < total_quantity;
```

---

## 4. 구현 내역

### 4.1 재고 차감 구현

#### Entity: Product.java
```java
@Entity
public class Product {
    @Id
    private Long id;

    @Version  // 낙관적 락
    private Long version;

    private Integer stock;

    public void decreaseStock(int quantity) {
        if (this.stock < quantity) {
            throw new IllegalStateException("재고가 부족합니다");
        }
        this.stock -= quantity;
    }
}
```

**파일 위치**: `src/main/java/com/hhplus/ecommerce/domain/product/Product.java:48`

#### Repository: ProductRepository.java
```java
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);
}
```

**파일 위치**: `src/main/java/com/hhplus/ecommerce/infrastructure/persistence/product/ProductRepository.java:50-52`

#### Service: OrderService.java
```java
@Service
@Transactional
@Retryable(
    value = {ObjectOptimisticLockingFailureException.class},
    maxAttempts = 5,
    backoff = @Backoff(delay = 50, maxDelay = 200, multiplier = 1.5)
)
public Order createOrder(Long userId, Long userCouponId, String idempotencyKey) {
    // 재고 조회 (낙관적 락)
    Product product = productRepository.findByIdWithLock(productId)
        .orElseThrow();

    // 재고 차감 (version 자동 증가)
    product.decreaseStock(quantity);

    // 커밋 시 version 검증
    // → 다른 트랜잭션이 먼저 업데이트했다면 예외 발생
    // → @Retryable로 자동 재시도
}
```

**파일 위치**: `src/main/java/com/hhplus/ecommerce/application/order/OrderService.java:110-114`

### 4.2 잔액 충전/차감 구현

#### Repository: UserRepository.java
```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithLock(@Param("id") Long id);
}
```

**파일 위치**: `src/main/java/com/hhplus/ecommerce/infrastructure/persistence/user/UserRepository.java:45-47`

#### Service: BalanceService.java
```java
@Service
@Transactional
public BigDecimal chargeBalance(Long userId, BigDecimal amount) {
    // 사용자 조회 (비관적 락 - SELECT FOR UPDATE)
    User user = userRepository.findByIdWithLock(userId)
        .orElseThrow();

    // 잔액 충전 (락 유지 중)
    user.chargeBalance(amount);

    // 이력 기록
    recordBalanceHistory(...);

    // 커밋 시 락 해제
    return user.getBalance();
}
```

**파일 위치**: `src/main/java/com/hhplus/ecommerce/application/user/BalanceService.java:68-100`

### 4.3 선착순 쿠폰 발급 구현

#### Entity: Coupon.java
```java
@Entity
public class Coupon {
    @Id
    private Long id;

    @Version  // 낙관적 락
    private Long version;

    private Integer totalQuantity;
    private Integer issuedQuantity;

    public void issue() {
        if (issuedQuantity >= totalQuantity) {
            throw new IllegalStateException("쿠폰이 모두 소진되었습니다");
        }
        this.issuedQuantity++;
    }
}
```

**파일 위치**: `src/main/java/com/hhplus/ecommerce/domain/coupon/Coupon.java:80`

#### Repository: CouponRepository.java
```java
@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdWithLock(@Param("id") Long id);
}
```

**파일 위치**: `src/main/java/com/hhplus/ecommerce/infrastructure/persistence/coupon/CouponRepository.java:51-53`

#### Service: CouponService.java
```java
@Service
@Transactional
@Retryable(
    value = {OptimisticLockingFailureException.class},
    maxAttempts = 5,
    backoff = @Backoff(delay = 50, maxDelay = 200, multiplier = 1.5)
)
public UserCoupon issueCoupon(Long userId, Long couponId) {
    // 쿠폰 조회 (낙관적 락)
    Coupon coupon = couponRepository.findByIdWithLock(couponId)
        .orElseThrow();

    // 발급 (version 자동 증가)
    coupon.issue();

    // 사용자 쿠폰 생성
    UserCoupon userCoupon = UserCoupon.builder()...;
    return userCouponRepository.save(userCoupon);
}
```

**파일 위치**: `src/main/java/com/hhplus/ecommerce/application/coupon/CouponService.java:96-101`

---

## 5. 테스트 결과

### 5.1 재고 차감 동시성 테스트

**테스트 코드**: `StockConcurrencyTest.java`

**시나리오**: 100명이 재고 10개 상품 동시 주문

```
=== 동시성 테스트 결과 ===
소요 시간: 3,245ms
성공: 10건
실패: 90건
완료 여부: true

=== 검증 ===
✅ 정확히 10명만 성공
✅ 최종 재고: 0개
✅ 상품 상태: OUT_OF_STOCK
✅ Version: 10 (10회 업데이트 확인)
```

**결과 분석**
- 낙관적 락으로 정확한 재고 제어 확인
- 재시도 메커니즘으로 성공률 향상
- 재고 소진 시 즉시 품절 처리

### 5.2 잔액 동시성 테스트

**테스트 코드**: `BalanceConcurrencyTest.java`

**시나리오 A**: 100개 스레드가 동시에 1,000원씩 충전

```
=== 동시성 테스트 결과 ===
총 소요 시간: 4,832ms
성공: 100건
실패: 0건

=== 잔액 검증 ===
초기 잔액: 10,000원
충전 횟수: 100회 × 1,000원
예상 최종 잔액: 110,000원
실제 최종 잔액: 110,000원
✅ 정확히 일치
```

**시나리오 B**: 충전 50회 + 주문(차감) 50회 동시 실행

```
=== 동시성 테스트 결과 ===
충전 성공: 50건
주문 성공: 50건
실패: 0건

=== 잔액 검증 ===
초기 잔액: 10,000원
충전 합계: +500,000원 (50회)
주문 합계: -250,000원 (50회)
예상 최종 잔액: 260,000원
실제 최종 잔액: 260,000원
✅ 데이터 정합성 보장
✅ 데드락 없이 정상 처리
```

**결과 분석**
- 비관적 락으로 100% 정확성 보장
- 충전과 차감 동시 처리 문제없음
- 락 대기 시간으로 인한 처리 시간 증가 (허용 범위)

### 5.3 선착순 쿠폰 발급 동시성 테스트

**테스트 코드**: `CouponServiceConcurrencyTest.java`

**시나리오**: 1,000명이 100개 쿠폰에 동시 요청

```
=== 동시성 테스트 결과 ===
소요 시간: 2,145ms
성공: 100건
실패: 900건

=== 검증 ===
✅ 정확히 100명만 발급
✅ DB 저장 수: 100개
✅ 쿠폰 발급 수량: 100/100
✅ 쿠폰 상태: EXHAUSTED
✅ 쿠폰 소진 에러: 890건
```

**결과 분석**
- 낙관적 락으로 정확한 선착순 보장
- 오버커밋 없음
- 높은 동시 요청 처리 가능

---

## 6. 결론

### 6.1 해결 효과

| 구분 | 개선 전 | 개선 후 | 효과 |
|------|---------|---------|------|
| **재고 정확성** | 오버셀링 발생 | 100% 정확 | ✅ 재무 손실 방지 |
| **잔액 정확성** | Lost Update | 100% 정확 | ✅ 금전 거래 신뢰성 확보 |
| **쿠폰 정확성** | 오버커밋 발생 | 선착순 보장 | ✅ 마케팅 비용 통제 |
| **동시 처리** | Race Condition | 안정적 처리 | ✅ 사용자 경험 개선 |

### 6.2 성능 영향

**낙관적 락 (재고, 쿠폰)**
- 읽기 성능: 영향 없음
- 쓰기 성능: 재시도로 인한 약간의 지연 (평균 50-200ms)
- 전체 처리량: 우수

**비관적 락 (잔액)**
- 읽기 성능: 영향 없음 (락 없는 조회)
- 쓰기 성능: 락 대기로 인한 지연 (평균 100-500ms)
- 전체 처리량: 중간 (허용 범위)

### 6.3 권장 사항

#### 1. 모니터링 강화
- 낙관적 락 충돌 빈도 모니터링
- 비관적 락 대기 시간 모니터링
- 데드락 발생 여부 모니터링

#### 2. 재시도 정책 최적화
- 현재: 최대 5회, 50-200ms 간격
- 트래픽에 따라 동적 조정 고려

#### 3. 인덱스 최적화
```sql
-- 재고 조회 최적화
CREATE INDEX idx_products_status_stock ON products(status, stock);

-- 쿠폰 조회 최적화
CREATE INDEX idx_coupons_status_issue_dates
ON coupons(status, issue_start_at, issue_end_at);
```

#### 4. 확장성 고려사항
- Redis 분산 락 도입 검토 (트래픽 증가 시)
- 쿠폰 발급: Redis Sorted Set + Lua Script
- 재고 차감: Redis Decrement + 비동기 동기화

#### 5. 장애 대응
- 낙관적 락 재시도 실패 시 알림
- 비관적 락 타임아웃 시 자동 재시도
- 데드락 발생 시 자동 복구

### 6.4 최종 평가

✅ **성공 요소**
1. 문제별 특성에 맞는 락 전략 선택
2. DB 기반 동시성 제어로 안정성 확보
3. 재시도 메커니즘으로 성공률 향상
4. 철저한 테스트로 검증 완료

⚠️ **개선 필요 영역**
1. 대규모 트래픽 대비 Redis 분산 락 준비
2. 락 타임아웃 최적화
3. 성능 모니터링 대시보드 구축

---

**작성일**: 2025-11-20
**작성자**: E-commerce Development Team
**문서 버전**: 1.0
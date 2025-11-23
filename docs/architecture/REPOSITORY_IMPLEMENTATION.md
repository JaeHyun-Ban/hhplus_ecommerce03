# Repository 구현 전략

> **Last Updated**: 2025-11-20
> **Version**: 3.0 (5주차 - InMemory Repository 제거)

## TL;DR (빠른 요약)

| 항목 | 내용 |
|------|------|
| **구현** | JPA Repository (Spring Data JPA + MySQL 8.0) |
| **프로파일** | dev (기본), prod, test (TestContainers) |
| **동시성 제어** | Pessimistic Lock (잔액), Optimistic Lock (재고, 쿠폰) |
| **테스트 전략** | TestContainers 기반 통합 테스트 (~260개 케이스) |
| **Repository 수** | 13개 (모두 JPA) |

---

## 개요

이 프로젝트는 **Spring Data JPA + MySQL 8.0** 기반으로 구현되어 있습니다.

### 특징
- Spring Data JPA 사용
- MySQL 8.0 (개발/운영 환경)
- 완전한 트랜잭션 관리
- **Pessimistic Lock** (잔액 충전/차감) + **Optimistic Lock** (재고 차감, 쿠폰 발급) 지원
- 페이징, 정렬, 복잡한 쿼리 지원
- TestContainers를 이용한 통합 테스트

---

## 아키텍처 다이어그램

```
┌─────────────────────────────────────────┐
│       Presentation Layer (Controller)    │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│      Application Layer (Service)         │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│   Infrastructure Layer (Repository IF)   │
└───────────────┬─────────────────────────┘
                │
                ▼
        ┌──────────────────┐
        │  JPA Repository  │
        │  (Spring Data)   │
        │                  │
        │  - MySQL 8.0     │
        │  - Transaction   │
        │  - JPA Lock      │
        └──────────────────┘
```

---

## 동시성 제어 전략

| Repository | Lock 방식 | 사용 이유 |
|------------|----------|----------|
| **UserRepository** | Pessimistic Lock | 잔액 차감은 절대 실패하면 안됨 (강한 일관성) |
| **ProductRepository** | Optimistic Lock | 재고 차감 시 충돌 발생 시 재시도 (성능 우선) |
| **CouponRepository** | Optimistic Lock | 선착순 쿠폰 발급 (동시성 높음) |
| **OrderRepository** | 멱등성 키 | 중복 주문 방지 (네트워크 재시도 대응) |

### Lock 방식 비교

| 항목 | Pessimistic Lock | Optimistic Lock |
|------|-----------------|-----------------|
| **메커니즘** | `SELECT ... FOR UPDATE` | `@Version` 필드 자동 증가 |
| **충돌 처리** | 대기 (블로킹) | 예외 발생 → 재시도 |
| **성능** | 낮음 (Lock 대기) | 높음 (충돌 시에만 재시도) |
| **사용 사례** | 잔액 충전/차감 (절대 실패 불가) | 재고 차감, 쿠폰 발급 (재시도 가능) |

---

## 프로파일 설정

### Dev 환경 (기본)
```bash
# MySQL 8.0 사용 (Docker)
./gradlew bootRun
```
→ MySQL이 localhost:3306에서 실행 중이어야 함

### Prod 환경
```bash
# MySQL 사용 (운영)
./gradlew bootRun --args='--spring.profiles.active=prod'
```
→ 환경변수로 DB 접속 정보 제공 필요

### Test 환경
```bash
# TestContainers MySQL 자동 실행
./gradlew test
```
→ Docker가 실행 중이어야 함 (TestContainers가 MySQL 컨테이너 자동 실행)

---

## 구현 상세

### 디렉토리 구조
```
src/main/java/com/hhplus/ecommerce/infrastructure/persistence/
├── user/
│   ├── UserRepository.java (JPA Interface - Pessimistic Lock)
│   └── BalanceHistoryRepository.java
├── product/
│   ├── ProductRepository.java (JPA Interface - Optimistic Lock)
│   ├── CategoryRepository.java
│   ├── ProductStatisticsRepository.java
│   ├── StockHistoryRepository.java
│   └── RestockNotificationRepository.java
├── cart/
│   ├── CartRepository.java
│   └── CartItemRepository.java
├── order/
│   ├── OrderRepository.java (멱등성 키 기반)
│   └── OrderSequenceRepository.java
├── coupon/
│   ├── CouponRepository.java (Optimistic Lock - 선착순)
│   └── UserCouponRepository.java (Optimistic Lock)
└── integration/
    └── OutboundEventRepository.java (이벤트 발행)
```

**특징**:
- **모든 Repository가 infrastructure 계층**에 위치
- **JPA Repository**: Spring Data JPA 인터페이스 상속
- **동시성 제어**: Repository 레벨에서 Lock 처리

### 주요 Repository 예시

#### 1) User Repository (Pessimistic Lock)
```java
// infrastructure/persistence/user/UserRepository.java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Pessimistic Lock으로 잔액 동시성 제어
     * SELECT ... FOR UPDATE
     *
     * Use Case: UC-001 잔액 충전, UC-012 주문 결제
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithLock(@Param("id") Long id);

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

#### 2) Product Repository (Optimistic Lock)
```java
// infrastructure/persistence/product/ProductRepository.java
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Optimistic Lock으로 재고 동시성 제어
     * @Version 필드 사용
     *
     * Use Case: UC-012 주문 생성 시 재고 차감
     */
    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT p FROM Product p WHERE p.status = 'AVAILABLE' AND p.stock > 0 " +
           "ORDER BY p.createdAt DESC")
    Page<Product> findAvailableProducts(Pageable pageable);

    List<Product> findByStatus(ProductStatus status);
}
```

#### 3) Coupon Repository (Optimistic Lock - 선착순)
```java
// infrastructure/persistence/coupon/CouponRepository.java
@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /**
     * Optimistic Lock으로 선착순 쿠폰 발급 동시성 제어
     * issuedQuantity 증가 시 version 체크
     *
     * Use Case: UC-017 쿠폰 발급
     */
    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdWithLock(@Param("id") Long id);

    List<Coupon> findByStatus(CouponStatus status);
}
```

#### 4) Order Repository (멱등성 키)
```java
// infrastructure/persistence/order/OrderRepository.java
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * 멱등성 키로 중복 주문 방지
     * 동일한 idempotencyKey로 재요청 시 기존 주문 반환
     *
     * Use Case: UC-012 주문 생성 (중복 결제 방지)
     */
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    Page<Order> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
}
```

---

## 테스트 전략

### 통합 테스트 (기본)
```java
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Test
    void 잔액충전_동시성_테스트() {
        // TestContainers MySQL 사용
        // 실제 DB 환경에서 동시성 제어 검증
    }
}
```

**특징**:
- TestContainers로 실제 MySQL 8.0 컨테이너 실행
- JPA Repository의 실제 Lock 메커니즘 테스트
- 260개 테스트 케이스 (18개 스킵 - Performance 테스트)
- JaCoCo 코드 커버리지 ~85%

### 테스트 커버리지

| 테스트 유형 | 개수 | 설명 |
|-----------|-----|------|
| **통합 테스트** | ~240개 | 실제 MySQL 환경 테스트 |
| **동시성 테스트** | ~20개 | 락 메커니즘 검증 |
| **성능 테스트** | 3개 (스킵) | 대용량 데이터 성능 측정 |

---

## 실제 사용 현황

### Production
- **프로파일**: dev (개발), prod (운영)
- **Repository**: JPA Repository (모든 도메인)
- **데이터베이스**: MySQL 8.0
- **동시성 제어**:
  - Pessimistic Lock (User 잔액)
  - Optimistic Lock (Product 재고, Coupon 발급)

### Testing
- **프로파일**: test
- **Repository**: JPA Repository
- **데이터베이스**: TestContainers MySQL 8.0
- **테스트 전략**: 통합 테스트 위주

---

## 결론

- **구현**: JPA Repository (Spring Data JPA + MySQL 8.0)
- **실제 사용**: MySQL 8.0 (dev, prod 환경)
- **테스트**: TestContainers + MySQL (통합 테스트)
- **동시성 제어**: Pessimistic Lock + Optimistic Lock + 멱등성 키
- **확장성**: 새로운 구현체 추가 용이 (Redis, MongoDB 등)

---

## 변경 이력

### Version 3.0 (2025-11-20) - 5주차
- ✅ InMemory Repository 제거 (미사용으로 확인)
- ✅ JPA Repository에만 집중
- ✅ 문서 간소화
- ✅ 테스트 전략 업데이트 (260개 테스트)

### Version 2.0 (2025-11-16) - 4주차
- ✅ 실제 프로젝트 구조 반영 (15개 Repository)
- ✅ H2 제거, MySQL 8.0만 사용
- ✅ 통합 테스트 전략 추가 (TestContainers)
- ✅ 동시성 제어 실제 사용 사례 추가
- ✅ Repository별 Lock 전략 상세 설명
- ✅ 멱등성 키 기반 중복 방지 추가 (OrderRepository)
- ✅ InMemory Repository 제한사항 명시 (User, Product만)

### Version 1.0 (초기 버전)
- JPA Repository 기본 구현
- InMemory Repository 프로토타입
- 프로파일 기반 전환

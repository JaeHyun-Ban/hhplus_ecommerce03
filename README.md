# E-Commerce Platform

> 항해플러스 백엔드 과정 - 4주차 과제
> 레이어드 아키텍처 기반 이커머스 플랫폼 구축 + 통합 테스트

[![Java](https://img.shields.io/badge/Java-17-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![JPA](https://img.shields.io/badge/JPA-Hibernate-blue)](https://hibernate.org/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

---

## 📋 목차

1. [프로젝트 개요](#-프로젝트-개요)
2. [주요 기능](#-주요-기능)
3. [기술 스택](#-기술-스택)
4. [아키텍처](#-아키텍처)
5. [동시성 제어](#-동시성-제어)
6. [실행 방법](#-실행-방법)
7. [API 문서](#-api-문서)
8. [테스트](#-테스트)
9. [프로젝트 구조](#-프로젝트-구조)

---

## 🎯 프로젝트 개요

### 비즈니스 도메인
사용자가 상품을 조회하고, 장바구니에 담고, 주문/결제하며, 쿠폰을 발급받아 사용할 수 있는 전자상거래 플랫폼입니다.


### 핵심 요구사항
- ✅ **레이어드 아키텍처**: 4계층(Presentation, Application, Domain, Infrastructure) 명확히 분리
- ✅ **도메인 주도 설계**: 풍부한 도메인 모델과 비즈니스 로직 캡슐화
- ✅ **동시성 제어**: Pessimistic Lock + Optimistic Lock을 통한 데이터 정합성 보장
- ✅ **선착순 쿠폰 발급**: Race Condition 방지
- ✅ **인메모리 저장소**: DB 없이 순수 메모리 저장소로 실행 가능
- ✅ **테스트 커버리지**: 단위 테스트 + 통합 테스트

---

## 🚀 주요 기능

### 1. 사용자 관리
- 사용자 등록/조회
- 잔액 충전 (Pessimistic Lock)
- 잔액 사용 내역 조회

### 2. 상품 관리
- 상품 목록 조회 (페이징)
- 상품 상세 조회
- 카테고리별 상품 조회
- 인기 상품 TOP 5 (최근 3일 판매량 기준)

### 3. 장바구니
- 장바구니 조회
- 상품 추가/수량 변경/삭제
- 장바구니 비우기

### 4. 주문/결제
- 주문 생성 (재고 차감 + 잔액 차감)
- 주문 조회 (사용자별, 주문번호별)
- 주문 취소 (재고 복구 + 잔액 환불)
- 멱등성 보장 (Idempotency Key)

### 5. 쿠폰
- 쿠폰 목록 조회
- 선착순 쿠폰 발급 (Optimistic Lock)
- 내 쿠폰 조회
- 주문 시 쿠폰 적용

---

## 🛠 기술 스택

### Backend
- **Language**: Java 17
- **Framework**: Spring Boot 3.x
- **ORM**: Spring Data JPA (Hibernate)
- **Build Tool**: Gradle 8.5

### Libraries
- **Validation**: Bean Validation (Hibernate Validator)
- **Documentation**: SpringDoc OpenAPI 3 (Swagger)
- **Logging**: SLF4J + Logback
- **Utility**: Lombok
- **Retry**: Spring Retry

### Testing
- **Unit Test**: JUnit 5, Mockito
- **Integration Test**: Spring Boot Test, TestContainers (MySQL)
- **Concurrency Test**: ExecutorService
- **Code Coverage**: JaCoCo

---

## 🏗 아키텍처

### 레이어드 아키텍처 (4-Tier)

```
┌─────────────────────────────────────────────┐
│         Presentation Layer                   │  ← HTTP 요청/응답, DTO 변환
│  (Controller, DTO, Exception Handler)        │
└───────────────┬─────────────────────────────┘
                │ depends on
                ▼
┌─────────────────────────────────────────────┐
│         Application Layer                    │  ← 유스케이스 실행, 트랜잭션
│  (Service, UseCase Orchestration)            │
└───────────────┬─────────────────────────────┘
                │ depends on
                ▼
┌─────────────────────────────────────────────┐
│         Domain Layer                         │  ← 비즈니스 로직, 엔티티
│  (Entity, Value Object, Domain Service)      │
└───────────────┬─────────────────────────────┘
                │ depends on
                ▼
┌─────────────────────────────────────────────┐
│         Infrastructure Layer                 │  ← 데이터 접근, 외부 통신
│  (Repository, External API)                  │
└─────────────────────────────────────────────┘
```

### 의존성 방향
**Domain** ← **Application** ← **Presentation**
**Domain** ← **Infrastructure**

> Domain Layer는 다른 계층에 의존하지 않음 (Dependency Inversion Principle)

---

## 🔒 동시성 제어

### 1. Pessimistic Lock (비관적 락)

**사용 사례**: 잔액 충전/차감

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT u FROM User u WHERE u.id = :id")
Optional<User> findByIdWithLock(@Param("id") Long id);
```

**특징**:
- `SELECT ... FOR UPDATE` SQL 생성
- 트랜잭션이 끝날 때까지 다른 트랜잭션의 읽기/쓰기 차단
- 데이터 정합성 100% 보장
- 잠금 대기로 인한 성능 저하 가능

**선택 이유**:
- 금액은 절대 틀려서는 안 되는 Critical한 데이터
- 충돌 확률이 높음 (같은 사용자가 빈번하게 접근)

---

### 2. Optimistic Lock (낙관적 락)

**사용 사례**: 상품 재고 차감, 선착순 쿠폰 발급

```java
@Entity
public class Product {
    @Version
    private Long version;

    private Integer stock;

    public void decreaseStock(Integer quantity) {
        if (this.stock < quantity) {
            throw new IllegalStateException("재고 부족");
        }
        this.stock -= quantity;
    }
}
```

**동작 방식**:
1. 엔티티 조회 시 `version` 필드 함께 조회
2. UPDATE 시 WHERE 절에 version 조건 추가
   ```sql
   UPDATE product
   SET stock = ?, version = version + 1
   WHERE id = ? AND version = ?
   ```
3. 영향받은 행이 0개면 `OptimisticLockingFailureException` 발생

**Retry 메커니즘**:
```java
@Retryable(
    value = OptimisticLockingFailureException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 100)
)
public Order createOrder(...) {
    // 주문 생성 로직
}
```

**특징**:
- Lock 없이 동작 (성능 우수)
- 충돌 발생 시에만 재시도
- 충돌 확률이 낮을 때 효율적

**선택 이유**:
- 재고/쿠폰은 읽기가 많고 쓰기가 적음
- 동시 접근은 많지만 동일 상품에 대한 동시 구매는 상대적으로 적음
- Pessimistic Lock 사용 시 성능 저하 우려

---

### 3. 동시성 제어 비교

| 항목 | Pessimistic Lock | Optimistic Lock |
|------|------------------|-----------------|
| **적용 대상** | 사용자 잔액 | 상품 재고, 쿠폰 |
| **Lock 방식** | DB Row Lock | Version Check |
| **충돌 처리** | 대기 (Blocking) | 재시도 (Retry) |
| **성능** | 낮음 (Lock 대기) | 높음 (Lock 없음) |
| **정합성** | 100% 보장 | 재시도로 보장 |
| **사용 시기** | 충돌 많음 + Critical | 충돌 적음 + 성능 중요 |

---

### 4. 선착순 쿠폰 발급 시나리오

**문제 상황**: 100개 쿠폰, 1000명 동시 요청 시 정확히 100명만 발급받아야 함

**해결 방법**:
1. `Coupon` 엔티티에 `@Version` 적용
2. 쿠폰 발급 시 `issuedQuantity` 증가
3. 동시에 여러 트랜잭션이 같은 쿠폰 수정 시도
4. 먼저 커밋한 트랜잭션만 성공, 나머지는 `OptimisticLockingFailureException`
5. `@Retryable`로 최대 3회 재시도
6. 재고 소진 시 예외 발생

**테스트 결과**:
- `CouponServiceConcurrencyTest`: 1000개 스레드로 동시 발급 테스트
- 정확히 100개만 발급됨 확인
- Race Condition 없음

---

## 🎮 실행 방법

### 1. 사전 요구사항
- Java 17 이상
- Gradle 8.5 이상 (또는 Gradle Wrapper 사용)

### 2. 프로젝트 클론
```bash
git clone https://github.com/your-username/ecommerce.git
cd ecommerce
```

### 3. 실행 모드 선택

#### 옵션 A: JPA + H2 (기본, 권장)
```bash
./gradlew bootRun

# 또는
./gradlew bootRun --args='--spring.profiles.active=local'
```

**접속 정보**:
- API: http://localhost:8080
- H2 Console: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:ecommerce`
  - Username: `sa`
  - Password: (비어있음)

#### 옵션 B: 순수 InMemory (DB 없음)
```bash
./gradlew bootRun --args='--spring.profiles.active=inmemory'
```

**특징**:
- DB 설정 불필요
- HashMap 기반 메모리 저장
- 애플리케이션 재시작 시 데이터 소멸

#### 옵션 C: MySQL (개발/운영)
```bash
# MySQL 8.0 설치 및 실행
mysql -u root -p
CREATE DATABASE ecommerce;
CREATE USER 'ecommerce_user'@'localhost' IDENTIFIED BY 'ecommerce_password';
GRANT ALL PRIVILEGES ON ecommerce.* TO 'ecommerce_user'@'localhost';

# 애플리케이션 실행
./gradlew bootRun --args='--spring.profiles.active=dev'
```

---

## 📚 API 문서

### Swagger UI
애플리케이션 실행 후 아래 주소로 접속:

**URL**: http://localhost:8080/swagger-ui.html

### 주요 API 엔드포인트

#### 사용자 (User)
```http
POST   /api/users                          # 사용자 등록
GET    /api/users/{userId}                 # 사용자 조회
POST   /api/users/{userId}/balance/charge  # 잔액 충전
GET    /api/users/{userId}/balance         # 잔액 조회
GET    /api/users/{userId}/balance/history # 잔액 이력
```

#### 상품 (Product)
```http
GET    /api/products                       # 상품 목록 (페이징)
GET    /api/products/{productId}           # 상품 상세
GET    /api/products/popular               # 인기 상품 TOP 5
GET    /api/products?categoryId={id}       # 카테고리별 상품
GET    /api/categories                     # 카테고리 목록
```

#### 장바구니 (Cart)
```http
GET    /api/carts/{userId}                 # 장바구니 조회
POST   /api/carts/{userId}/items           # 상품 추가
PUT    /api/carts/items/{cartItemId}       # 수량 변경
DELETE /api/carts/items/{cartItemId}       # 항목 삭제
DELETE /api/carts/{userId}/items           # 장바구니 비우기
```

#### 주문 (Order)
```http
POST   /api/orders                         # 주문 생성
GET    /api/orders/{orderId}               # 주문 조회
POST   /api/orders/{orderId}/cancel        # 주문 취소
GET    /api/users/{userId}/orders          # 내 주문 목록
```

#### 쿠폰 (Coupon)
```http
GET    /api/coupons                        # 쿠폰 목록
POST   /api/coupons/{couponId}/issue       # 쿠폰 발급
GET    /api/users/{userId}/coupons         # 내 쿠폰 목록
```

---

## 🧪 테스트

### 테스트 실행
```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests "CouponServiceConcurrencyTest"
```

### 테스트 커버리지 확인 (JaCoCo)
```bash
# 테스트 + 커버리지 리포트 생성
./gradlew test jacocoTestReport

# HTML 리포트 확인
open build/reports/jacoco/test/html/index.html
```

**커버리지 제외 대상**:
- Config 클래스
- DTO, Request, Response 클래스
- Exception, Enum 클래스
- Application 메인 클래스

### 테스트 전략

#### 1. 단위 테스트 (Unit Test)
Mock 객체를 사용하여 비즈니스 로직만 독립적으로 테스트

**예시**:
- `UserServiceTest`: 사용자 생성/조회 로직 검증
- `BalanceServiceTest`: 잔액 충전/사용 로직 검증
- `ProductServiceTest`: 상품 조회/재고 관리 로직 검증

#### 2. 통합 테스트 (Integration Test)
TestContainers를 사용하여 실제 MySQL 컨테이너 환경에서 테스트

**주요 통합 테스트**:

**사용자 & 잔액**:
- `UserServiceIntegrationTest`: 사용자 생성, 조회, 잔액 관리 통합 테스트

**쿠폰**:
- `CouponIssueIntegrationTest`: 선착순 쿠폰 발급 검증
- `CouponQueryIntegrationTest`: 쿠폰 조회 기능 검증
- `UserCouponIntegrationTest`: 사용자 쿠폰 관리 검증

**장바구니**:
- `CartItemAddIntegrationTest`: 장바구니 상품 추가 검증
- `CartItemManageIntegrationTest`: 장바구니 수량 변경/삭제 검증
- `CartQueryIntegrationTest`: 장바구니 조회 검증

**주문**:
- `OrderCreateIntegrationTest`: 주문 생성 플로우 검증 (재고/잔액/쿠폰 통합)
- `OrderQueryIntegrationTest`: 주문 조회 및 목록 검증

#### 3. 동시성 테스트 (Concurrency Test)
멀티 스레드 환경에서 동시성 제어 검증

**예시**:
- `CouponServiceConcurrencyTest`
  - 1000개 스레드 동시 쿠폰 발급
  - 정확히 100개만 발급되는지 검증
  - Optimistic Lock + Retry 메커니즘 검증
  - Race Condition 방지 확인

### TestContainers 설정

통합 테스트는 Docker 기반 MySQL 컨테이너를 자동으로 생성/실행합니다.

```java
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
class CouponIssueIntegrationTest {
    // 실제 DB 환경에서 통합 테스트 수행
}
```

**특징**:
- 테스트마다 독립된 DB 컨테이너 생성
- 테스트 완료 후 자동으로 컨테이너 제거
- 실제 운영 환경과 동일한 DB 동작 보장

---

## 📁 프로젝트 구조

```
ecommerce/
├── src/
│   ├── main/
│   │   ├── java/com/hhplus/ecommerce/
│   │   │   ├── presentation/          # Presentation Layer
│   │   │   │   ├── api/
│   │   │   │   │   ├── user/         # UserController, DTO
│   │   │   │   │   ├── product/      # ProductController, CategoryController
│   │   │   │   │   ├── cart/         # CartController
│   │   │   │   │   ├── order/        # OrderController
│   │   │   │   │   └── coupon/       # CouponController
│   │   │   │   └── exception/        # GlobalExceptionHandler
│   │   │   ├── application/           # Application Layer
│   │   │   │   ├── user/             # UserService, BalanceService
│   │   │   │   ├── product/          # ProductService, ProductStatisticsService
│   │   │   │   ├── cart/             # CartService
│   │   │   │   ├── order/            # OrderService
│   │   │   │   └── coupon/           # CouponService
│   │   │   ├── domain/                # Domain Layer
│   │   │   │   ├── user/             # User, UserRole, UserStatus, BalanceHistory
│   │   │   │   ├── product/          # Product, Category, ProductStatistics
│   │   │   │   ├── cart/             # Cart, CartItem
│   │   │   │   ├── order/            # Order, OrderItem, Payment, OrderStatus
│   │   │   │   ├── coupon/           # Coupon, UserCoupon, OrderCoupon
│   │   │   │   └── common/           # BaseEntity
│   │   │   ├── infrastructure/        # Infrastructure Layer
│   │   │   │   └── persistence/
│   │   │   │       ├── user/         # UserRepository (JPA)
│   │   │   │       ├── product/      # ProductRepository (JPA)
│   │   │   │       ├── cart/         # CartRepository (JPA)
│   │   │   │       ├── order/        # OrderRepository (JPA)
│   │   │   │       ├── coupon/       # CouponRepository (JPA)
│   │   │   │       └── inmemory/     # InMemory 구현체
│   │   │   └── config/               # 설정 클래스
│   │   │       ├── JpaConfig.java
│   │   │       ├── OpenApiConfig.java
│   │   │       └── SchedulerConfig.java
│   │   └── resources/
│   │       ├── application.yml        # 설정 파일
│   │       └── data.sql               # 초기 데이터 (Optional)
│   └── test/
│       └── java/com/hhplus/ecommerce/
│           ├── config/                # 테스트 설정
│           │   └── TestContainersConfig.java
│           ├── application/           # 서비스 테스트
│           │   ├── user/
│           │   │   ├── UserServiceTest.java              # 단위 테스트
│           │   │   ├── BalanceServiceTest.java           # 단위 테스트
│           │   │   └── UserServiceIntegrationTest.java   # 통합 테스트
│           │   ├── product/
│           │   │   └── ProductServiceTest.java           # 단위 테스트
│           │   ├── cart/
│           │   │   ├── CartItemAddIntegrationTest.java   # 통합 테스트
│           │   │   ├── CartItemManageIntegrationTest.java # 통합 테스트
│           │   │   └── CartQueryIntegrationTest.java     # 통합 테스트
│           │   ├── order/
│           │   │   ├── OrderCreateIntegrationTest.java   # 통합 테스트
│           │   │   └── OrderQueryIntegrationTest.java    # 통합 테스트
│           │   └── coupon/
│           │       ├── CouponServiceConcurrencyTest.java # 동시성 테스트
│           │       ├── CouponServiceIntegrationTest.java # 통합 테스트
│           │       ├── CouponIssueIntegrationTest.java   # 통합 테스트
│           │       ├── CouponQueryIntegrationTest.java   # 통합 테스트
│           │       └── UserCouponIntegrationTest.java    # 통합 테스트
│           └── EcommerceApplicationTests.java
├── docs/                              # 문서
│   ├── api-specs/                    # API 명세서
│   ├── design/                       # 설계 문서
│   │   ├── domain-design.md
│   │   ├── erd-diagram.dbml
│   │   └── sequence-diagrams-mermaid.md
│   ├── architecture/                 # 아키텍처 문서
│   │   └── REPOSITORY_IMPLEMENTATION.md
│   ├── requirements/                 # 요구사항
│   └── guides/                       # 가이드
├── scripts/                          # 유틸리티 스크립트
├── build.gradle                      # Gradle 빌드 설정
├── settings.gradle
└── README.md                         # 프로젝트 소개 (이 파일)
```

---

## 🎓 학습 포인트

### 1. 레이어드 아키텍처
- 각 계층의 책임 명확히 분리
- 의존성 방향 준수 (Domain은 독립적)
- 테스트 가능한 구조

### 2. 동시성 제어
- Pessimistic Lock vs Optimistic Lock 비교
- 실제 상황에서의 Lock 전략 선택 기준
- Retry 메커니즘 구현

### 3. 도메인 주도 설계
- 풍부한 도메인 모델 (Anemic Model 지양)
- 비즈니스 로직을 도메인 계층에 캡슐화
- Value Object, Enum 활용

### 4. Repository 패턴
- 인터페이스와 구현체 분리
- JPA Repository ↔ InMemory Repository 전환 가능
- 테스트 용이성 확보

### 5. 통합 테스트 전략
- TestContainers를 활용한 실제 DB 환경 테스트
- 단위 테스트와 통합 테스트의 명확한 분리
- 도메인별 테스트 시나리오 설계 (Issue, Query, Manage 등)
- JaCoCo를 통한 코드 커버리지 측정

---

## 📊 성능 고려사항

### N+1 문제 해결
```java
@Query("SELECT o FROM Order o " +
       "LEFT JOIN FETCH o.orderItems oi " +
       "LEFT JOIN FETCH oi.product " +
       "WHERE o.id = :id")
Optional<Order> findByIdWithDetails(@Param("id") Long id);
```

### 페이징 처리
```java
Page<Product> findAvailableProducts(Pageable pageable);
```

### 인덱스 전략
- 복합 인덱스: `(user_id, status, created_at)`
- Unique 인덱스: `email`, `orderNumber`, `idempotencyKey`

---

## 🚧 향후 개선 사항

- [ ] 재입고 알림 기능 (UC-020)
- [ ] Redis 캐싱 (인기 상품, 카테고리)
- [ ] 이벤트 기반 아키텍처 (주문 완료 → 알림)
- [ ] API Rate Limiting
- [ ] 로그 모니터링 (ELK Stack)
- [ ] Docker / Kubernetes 배포
- [ ] CI/CD Pipeline (GitHub Actions)

---

## 📝 라이센스

MIT License

---

## 👥 작성자

**항해플러스 백엔드 과정**
GitHub: [@your-username](https://github.com/your-username)

---

## 🙏 감사의 글

이 프로젝트는 항해플러스 백엔드 과정의 일환으로 작성되었습니다.
동시성 제어, 레이어드 아키텍처, 도메인 주도 설계에 대한 실무 경험을 쌓을 수 있었습니다.

---

**Last Updated**: 2025-11-16
**Version**: 1.1.0 (Week 4 - Integration Tests)

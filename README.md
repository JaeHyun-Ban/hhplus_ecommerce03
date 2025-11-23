# E-Commerce Platform

> 항해플러스 백엔드 과정 - 5주차 과제
> 레이어드 아키텍처 기반 이커머스 플랫폼 구축 + 동시성 제어 + 통합 테스트

[![Java](https://img.shields.io/badge/Java-17-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![JPA](https://img.shields.io/badge/JPA-Hibernate-blue)](https://hibernate.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)](https://www.mysql.com/)
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
- ✅ **주문 번호 시퀀스**: 날짜별 순차 생성 (ORD-20251120-000001)
- ✅ **테스트 커버리지**: 통합 테스트 260개 + JaCoCo 85%+

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
- 주문 번호 자동 생성 (날짜별 시퀀스)
- 주문 조회 (사용자별, 주문번호별)
- 주문 취소 (재고 복구 + 잔액 환불)
- 멱등성 보장 (Idempotency Key)
- 결제 정보 관리 (Payment 엔티티)

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
- **Database**: MySQL 8.0
- **Build Tool**: Gradle 8.5

### Libraries
- **Validation**: Bean Validation (Hibernate Validator)
- **Documentation**: SpringDoc OpenAPI 3 (Swagger)
- **Logging**: SLF4J + Logback
- **Utility**: Lombok
- **Retry**: Spring Retry

### Testing
- **Framework**: JUnit 5
- **Integration Test**: Spring Boot Test, TestContainers (MySQL 8.0)
- **Concurrency Test**: ExecutorService, CountDownLatch
- **Code Coverage**: JaCoCo (85%+)

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

**사용 사례**: 잔액 충전/차감, 주문 번호 시퀀스 생성

```java
// 사용자 잔액
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT u FROM User u WHERE u.id = :id")
Optional<User> findByIdWithLock(@Param("id") Long id);

// 주문 번호 시퀀스
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT os FROM OrderSequence os WHERE os.date = :date")
Optional<OrderSequence> findByDateWithLock(@Param("date") String date);
```

**특징**:
- `SELECT ... FOR UPDATE` SQL 생성
- 트랜잭션이 끝날 때까지 다른 트랜잭션의 읽기/쓰기 차단
- 데이터 정합성 100% 보장
- 잠금 대기로 인한 성능 저하 가능

**선택 이유**:
- 금액과 주문 번호는 절대 틀려서는 안 되는 Critical한 데이터
- 충돌 확률이 매우 높음 (같은 사용자가 빈번하게 접근, 같은 날짜에 동시 주문)

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
    maxAttempts = 5,
    backoff = @Backoff(delay = 50, maxDelay = 200, multiplier = 1.5)
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
| **적용 대상** | 사용자 잔액, 주문 시퀀스 | 상품 재고, 쿠폰 |
| **Lock 방식** | DB Row Lock | Version Check |
| **충돌 처리** | 대기 (Blocking) | 재시도 (Retry) |
| **성능** | 낮음 (Lock 대기) | 높음 (Lock 없음) |
| **정합성** | 100% 보장 | 재시도로 보장 |
| **사용 시기** | 충돌 많음 + Critical | 충돌 적음 + 성능 중요 |

---

### 4. 동시성 테스트 현황

**재고 차감 테스트** (`StockConcurrencyTest`):
- ✅ 50명이 10개 재고 상품에 동시 주문 → 정확히 10명만 성공
- ✅ Optimistic Lock + Retry 메커니즘 동작 확인

**잔액 테스트** (`BalanceConcurrencyTest`):
- ✅ 동일 사용자 20개 동시 충전 → 모두 정확히 반영
- ✅ 충전 + 차감 동시 실행 → 정확한 잔액 유지
- ✅ Pessimistic Lock으로 순차 처리 확인

**쿠폰 테스트** (`CouponServiceConcurrencyTest`):
- ✅ 1000명이 100개 쿠폰에 동시 요청 → 정확히 100명만 발급
- ✅ 동일 사용자 중복 발급 방지 확인

**주문 통합 테스트** (`OrderIntegrationConcurrencyTest`):
- ✅ 여러 사용자 동시 주문 시 재고/잔액/쿠폰 정합성 보장
- ✅ 멱등성 키로 중복 주문 방지

**데드락 방지 테스트** (`DeadlockPreventionTest`):
- ✅ 50명이 동시 충전 + 주문 실행 → 데드락 없이 완료
- ✅ 비관적 락 순서 고정으로 교차 락 방지

---

## 🎮 실행 방법

### 1. 사전 요구사항
- Java 17 이상
- Gradle 8.5 이상 (또는 Gradle Wrapper 사용)
- Docker (TestContainers용)
- MySQL 8.0 (개발 환경)

### 2. 프로젝트 클론
```bash
git clone https://github.com/your-username/ecommerce.git
cd ecommerce
```

### 3. 데이터베이스 설정

#### MySQL 설치 및 실행
```bash
# Docker로 MySQL 실행
docker run -d \
  --name ecommerce-mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=123123 \
  -e MYSQL_DATABASE=mydb \
  mysql:8.0

# 또는 로컬 MySQL 설치
brew install mysql@8.0
mysql.server start
```

#### 데이터베이스 초기화
```bash
# MySQL 접속
mysql -u root -p

# 데이터베이스 생성
CREATE DATABASE mydb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 초기 스키마 및 데이터 적용
mysql -u root -p mydb < scripts/init.sql
```

### 4. 애플리케이션 실행

#### 개발 환경 (기본)
```bash
./gradlew bootRun

# 또는 프로파일 명시
./gradlew bootRun --args='--spring.profiles.active=dev'
```

**접속 정보**:
- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html

#### 운영 환경
```bash
./gradlew bootRun --args='--spring.profiles.active=prod'
```

---

## 📚 API 문서

### Swagger UI
애플리케이션 실행 후 아래 주소로 접속:

**URL**: http://localhost:8080/swagger-ui.html

### 주요 API 엔드포인트

#### 사용자 (User)
```http
POST   /api/v1/users                          # 사용자 등록
GET    /api/v1/users/{userId}                 # 사용자 조회
POST   /api/v1/users/{userId}/balance/charge  # 잔액 충전
GET    /api/v1/users/{userId}/balance         # 잔액 조회
GET    /api/v1/users/{userId}/balance/history # 잔액 이력
```

#### 상품 (Product)
```http
GET    /api/v1/products                       # 상품 목록 (페이징)
GET    /api/v1/products/{productId}           # 상품 상세
GET    /api/v1/products/popular               # 인기 상품 TOP 5
GET    /api/v1/products?categoryId={id}       # 카테고리별 상품
GET    /api/v1/categories                     # 카테고리 목록
```

#### 장바구니 (Cart)
```http
GET    /api/v1/carts/{userId}                 # 장바구니 조회
POST   /api/v1/carts/{userId}/items           # 상품 추가
PUT    /api/v1/carts/items/{cartItemId}       # 수량 변경
DELETE /api/v1/carts/items/{cartItemId}       # 항목 삭제
DELETE /api/v1/carts/{userId}/items           # 장바구니 비우기
```

#### 주문 (Order)
```http
POST   /api/v1/orders                         # 주문 생성
GET    /api/v1/orders/{orderId}               # 주문 조회
POST   /api/v1/orders/{orderId}/cancel        # 주문 취소
GET    /api/v1/users/{userId}/orders          # 내 주문 목록
```

#### 쿠폰 (Coupon)
```http
GET    /api/v1/coupons                        # 쿠폰 목록
POST   /api/v1/coupons/{couponId}/issue       # 쿠폰 발급
GET    /api/v1/users/{userId}/coupons         # 내 쿠폰 목록
```

---

## 🧪 테스트

### 테스트 실행
```bash
# 전체 테스트 실행 (약 3분 소요)
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests "CouponServiceConcurrencyTest"

# 동시성 테스트만 실행
./gradlew test --tests "*ConcurrencyTest"
```

### 테스트 커버리지 확인 (JaCoCo)
```bash
# 테스트 + 커버리지 리포트 생성
./gradlew test jacocoTestReport

# HTML 리포트 확인
open build/reports/jacoco/test/html/index.html
```

**현재 커버리지**: 85%+

**커버리지 제외 대상**:
- Config 클래스
- DTO, Request, Response 클래스
- Exception, Enum 클래스
- Application 메인 클래스

### 테스트 전략

#### 1. 통합 테스트 (Integration Test)
TestContainers를 사용하여 실제 MySQL 8.0 컨테이너 환경에서 테스트

**주요 통합 테스트**:

**사용자 & 잔액** (3개 파일):
- `UserServiceIntegrationTest`: 사용자 CRUD
- `BalanceServiceIntegrationTest`: 잔액 충전/사용/환불
- `BalanceConcurrencyTest`: 잔액 동시성 (20개 테스트)

**쿠폰** (2개 파일):
- `CouponServiceIntegrationTest`: 쿠폰 발급/조회 (~60개 테스트)
- `CouponServiceConcurrencyTest`: 선착순 동시성 (3개 테스트, 1개 스킵)

**장바구니** (1개 파일):
- `CartServiceIntegrationTest`: 장바구니 CRUD (~60개 테스트)

**상품** (1개 파일):
- `ProductStatisticsServiceTest`: 상품 통계 (~30개 테스트)

**주문** (3개 파일):
- `OrderServiceIntegrationTest`: 주문 생성/취소/조회
- `OrderSequenceConcurrencyTest`: 주문 번호 동시성
- `OrderIntegrationConcurrencyTest`: 통합 동시성

**재고** (1개 파일):
- `StockConcurrencyTest`: 재고 차감 동시성 (1개 스킵)

**데드락** (1개 파일):
- `DeadlockPreventionTest`: 데드락 방지 (2개 테스트)

**성능** (3개 파일, 모두 스킵):
- `LargeScaleIndexPerformanceTest`: 1000만 건 성능
- `PopularProductIndexPerformanceTest`: 100만 건 성능
- `ExtendedDateRangeIndexPerformanceTest`: 100만 건 성능

#### 2. 동시성 테스트 (Concurrency Test)
멀티 스레드 환경에서 동시성 제어 검증

**테스트 현황**:
- 총 260개 테스트
- 통과: 242개
- 스킵: 18개 (성능 테스트 15개 + 불안정 테스트 3개)

**동시성 테스트 시나리오**:
- 50명 동시 재고 차감
- 20명 동시 잔액 충전
- 1000명 선착순 쿠폰 발급
- 50명 동시 주문 번호 생성
- 50명 동시 충전 + 주문 (데드락 방지)

### TestContainers 설정

통합 테스트는 Docker 기반 MySQL 8.0 컨테이너를 자동으로 생성/실행합니다.

```java
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
class OrderServiceIntegrationTest {
    // 실제 DB 환경에서 통합 테스트 수행
}
```

**특징**:
- 테스트마다 독립된 DB 컨테이너 생성
- 테스트 완료 후 자동으로 컨테이너 제거
- 실제 운영 환경과 동일한 DB 동작 보장
- MySQL 8.0 정확한 동시성 제어 검증

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
│   │   │   │   ├── order/            # OrderService, OrderSequenceService
│   │   │   │   └── coupon/           # CouponService
│   │   │   ├── domain/                # Domain Layer
│   │   │   │   ├── user/             # User, UserRole, BalanceHistory
│   │   │   │   ├── product/          # Product, Category, ProductStatistics
│   │   │   │   ├── cart/             # Cart, CartItem
│   │   │   │   ├── order/            # Order, OrderItem, OrderSequence, Payment
│   │   │   │   ├── coupon/           # Coupon, UserCoupon, OrderCoupon
│   │   │   │   ├── integration/      # OutboundEvent
│   │   │   │   └── common/           # BaseEntity
│   │   │   ├── infrastructure/        # Infrastructure Layer
│   │   │   │   └── persistence/
│   │   │   │       ├── user/         # UserRepository (JPA)
│   │   │   │       ├── product/      # ProductRepository (JPA)
│   │   │   │       ├── cart/         # CartRepository (JPA)
│   │   │   │       ├── order/        # OrderRepository, OrderSequenceRepository
│   │   │   │       ├── coupon/       # CouponRepository (JPA)
│   │   │   │       └── integration/  # OutboundEventRepository
│   │   │   └── config/               # 설정 클래스
│   │   │       ├── JpaConfig.java
│   │   │       ├── OpenApiConfig.java
│   │   │       └── RetryConfig.java
│   │   └── resources/
│   │       └── application.yml        # 설정 파일 (dev, prod, test)
│   └── test/
│       └── java/com/hhplus/ecommerce/
│           ├── config/                # 테스트 설정
│           │   └── TestContainersConfig.java
│           ├── application/           # 서비스 테스트
│           │   ├── user/
│           │   │   ├── BalanceServiceIntegrationTest.java
│           │   │   └── BalanceConcurrencyTest.java
│           │   ├── product/
│           │   │   └── ProductStatisticsServiceTest.java
│           │   ├── cart/
│           │   │   └── CartServiceIntegrationTest.java
│           │   ├── order/
│           │   │   ├── OrderServiceIntegrationTest.java
│           │   │   ├── OrderSequenceConcurrencyTest.java
│           │   │   ├── OrderIntegrationConcurrencyTest.java
│           │   │   ├── StockConcurrencyTest.java
│           │   │   └── DeadlockPreventionTest.java
│           │   └── coupon/
│           │       ├── CouponServiceIntegrationTest.java
│           │       └── CouponServiceConcurrencyTest.java
│           ├── performance/           # 성능 테스트 (스킵)
│           │   ├── LargeScaleIndexPerformanceTest.java
│           │   ├── PopularProductIndexPerformanceTest.java
│           │   └── ExtendedDateRangeIndexPerformanceTest.java
│           └── EcommerceApplicationTests.java
├── docs/                              # 문서
│   ├── api-specs/                    # API 명세서
│   ├── design/                       # 설계 문서
│   │   ├── domain-design.md
│   │   ├── erd-diagram.dbml
│   │   └── sequence-diagrams-mermaid.md
│   ├── architecture/                 # 아키텍처 문서
│   │   └── REPOSITORY_IMPLEMENTATION.md
│   ├── performance/                  # 성능 문서
│   │   └── CONCURRENCY_SOLUTION_REPORT.md
│   └── testing/                      # 테스트 가이드
│       └── TEST_GUIDE.md
├── scripts/                          # SQL 스크립트
│   └── init.sql                      # 데이터베이스 초기화
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
- Retry 메커니즘 구현 (`@Retryable`)
- 데드락 방지 (락 획득 순서 고정)

### 3. 도메인 주도 설계
- 풍부한 도메인 모델 (Anemic Model 지양)
- 비즈니스 로직을 도메인 계층에 캡슐화
- Value Object, Enum 활용

### 4. Repository 패턴
- 인터페이스와 구현체 분리
- Spring Data JPA Repository 활용
- 테스트 용이성 확보

### 5. 통합 테스트 전략
- TestContainers를 활용한 실제 DB 환경 테스트
- 동시성 테스트 (ExecutorService, CountDownLatch)
- 도메인별 테스트 시나리오 설계
- JaCoCo를 통한 코드 커버리지 측정 (85%+)

### 6. 주문 번호 관리
- 날짜별 시퀀스 분리 (OrderSequence 엔티티)
- 비관적 락으로 동시성 제어
- 형식: ORD-YYYYMMDD-NNNNNN

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
- 복합 인덱스: `(status, stock)`, `(product_id, date)`
- Unique 인덱스: `email`, `orderNumber`, `idempotencyKey`
- 날짜 범위 인덱스: `created_at`, `ordered_at`

### 동시성 성능
- **잔액**: Pessimistic Lock (순차 처리, 정확성 우선)
- **재고**: Optimistic Lock + Retry (병렬 처리, 성능 우선)
- **쿠폰**: Optimistic Lock + Retry (선착순 보장)
- **주문 번호**: Pessimistic Lock (충돌 방지, 순차성 보장)

---

## 🚀 주요 개선사항 (v2.0.0)

### 5주차 개선사항
- ✅ InMemory Repository 제거 (MySQL만 사용)
- ✅ 주문 번호 시퀀스 관리 추가 (OrderSequence)
- ✅ 결제 엔티티 추가 (Payment)
- ✅ 동시성 테스트 강화 (260개 테스트)
- ✅ 데드락 방지 테스트 추가
- ✅ 성능 테스트 추가 (1000만 건)
- ✅ 테스트 격리 전략 개선 (@DirtiesContext)
- ✅ 문서 업데이트 (init.sql, README.md)

---

**Last Updated**: 2025-11-20
**Version**: 2.0.0 (Week 5 - Concurrency & Performance)

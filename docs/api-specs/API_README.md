# E-Commerce API Documentation

> **Last Updated**: 2025-11-20
> **Version**: 3.0 (5주차 - MySQL 단일화, 신규 기능 추가)
> **프로젝트 전체 정보**: [메인 README](../../README.md) | [문서 가이드](../README.md)

---

## 📚 문서 목차

| 문서 | 설명 | 링크 |
|-----|------|------|
| **OpenAPI Spec** | Swagger/OpenAPI 3.0 명세서 | [openapi.yaml](../assignment/openapi.yaml) |
| **RESTful API** | API 엔드포인트 상세 설명 | [restful-api-endpoints.md](restful-api-endpoints.md) |
| **Swagger Guide** | Swagger UI 사용 가이드 | [../guides/SWAGGER_GUIDE.md](../guides/SWAGGER_GUIDE.md) |
| **Sequence Diagrams** | 비즈니스 플로우 시퀀스 다이어그램 | [../design/sequence-diagrams-mermaid.md](../design/sequence-diagrams-mermaid.md) |
| **Domain Design** | 도메인 및 엔티티 설계 | [../design/domain-design.md](../design/domain-design.md) |
| **User Stories** | 사용자 스토리 | [../requirements/user-stories.md](../requirements/user-stories.md) |
| **Requirements** | 요구사항 명세 | [../requirements/requirements.md](../requirements/requirements.md) |

---

## 🚀 빠른 시작

### 1. Swagger UI로 API 문서 보기

```bash
# Spring Boot 실행
./gradlew bootRun

# Swagger UI 접속
open http://localhost:8080/swagger-ui.html
```

### 2. 온라인 에디터로 보기

https://editor.swagger.io 에서 `openapi.yaml` 파일 내용을 붙여넣기

### 3. Postman으로 Import

Postman → Import → `openapi.yaml` 선택

---

## 📖 API 개요

### 기본 정보

- **Base URL (Local):** `http://localhost:8080`
- **API Version:** v1
- **API Prefix:** `/api`
- **Authentication:** ⚠️ 현재 미구현 (향후 JWT Bearer Token 예정)
- **Spring Boot Version:** 3.5.7
- **Java Version:** 17

### 지원하는 기능

| 도메인 | 기능 | 엔드포인트 |
|-------|------|----------|
| **Users** | 사용자 관리, 잔액 충전/조회 | `/api/users/*` |
| **Products** | 상품 조회, 인기 상품 | `/api/products/*` |
| **Cart** | 장바구니 관리 (추가, 수정, 삭제) | `/api/carts/*` |
| **Orders** | 주문 생성, 결제, 취소, 조회 | `/api/orders/*` |
| **Coupons** | 쿠폰 발급, 조회 (선착순) | `/api/coupons/*` |

---

## 📋 주요 API 엔드포인트

### 사용자 (Users)

| Method | Endpoint | 설명 | Use Case |
|--------|----------|------|----------|
| POST | `/api/users` | 사용자 등록 | UC-002 |
| GET | `/api/users/{userId}` | 사용자 조회 | UC-003 |
| GET | `/api/users/{userId}/balance` | 잔액 조회 | UC-004 |
| POST | `/api/users/{userId}/balance/charge` | 잔액 충전 | UC-001 |
| GET | `/api/users/{userId}/balance/history` | 잔액 이력 조회 | UC-005 |

### 상품 (Products)

| Method | Endpoint | 설명 | Use Case |
|--------|----------|------|----------|
| GET | `/api/products` | 상품 목록 조회 (페이징) | UC-003 |
| GET | `/api/products?categoryId={id}` | 카테고리별 상품 조회 | UC-003 |
| GET | `/api/products/{productId}` | 상품 상세 조회 | UC-004 |
| GET | `/api/products/popular` | 인기 상품 조회 (Top 5) | UC-006 |
| GET | `/api/categories` | 카테고리 목록 | - |

### 장바구니 (Cart)

| Method | Endpoint | 설명 | Use Case |
|--------|----------|------|----------|
| GET | `/api/carts/{userId}` | 장바구니 조회 | UC-007 |
| POST | `/api/carts/{userId}/items` | 상품 추가 | UC-008 |
| PUT | `/api/carts/items/{cartItemId}` | 수량 변경 | UC-009 |
| DELETE | `/api/carts/items/{cartItemId}` | 상품 삭제 | UC-010 |
| DELETE | `/api/carts/{userId}/items` | 장바구니 비우기 | UC-011 |

### 주문 (Orders)

| Method | Endpoint | 설명 | Use Case |
|--------|----------|------|----------|
| POST | `/api/orders` | 주문 생성 (결제) | UC-012 |
| GET | `/api/orders/{orderId}` | 주문 상세 조회 | UC-013 |
| GET | `/api/orders?userId={userId}` | 사용자별 주문 목록 | UC-014 |
| POST | `/api/orders/{orderId}/cancel` | 주문 취소 | UC-015 |

### 쿠폰 (Coupons)

| Method | Endpoint | 설명 | Use Case |
|--------|----------|------|----------|
| GET | `/api/coupons` | 발급 가능한 쿠폰 목록 | UC-018 |
| POST | `/api/coupons/{couponId}/issue` | 쿠폰 발급 (선착순) | UC-017 |
| GET | `/api/users/{userId}/coupons` | 보유 쿠폰 조회 | UC-019 |

---

## 💡 API 사용 예시

### 1. 잔액 충전

```bash
curl -X POST 'http://localhost:8080/api/users/1/balance/charge' \
  -H 'Content-Type: application/json' \
  -d '{
    "amount": 10000
  }'
```

**응답:**
```json
{
  "id": 1,
  "userId": 1,
  "transactionType": "CHARGE",
  "amount": 10000,
  "balanceBefore": 50000,
  "balanceAfter": 60000,
  "createdAt": "2025-11-16T12:30:00"
}
```

### 2. 장바구니에 상품 추가

```bash
curl -X POST 'http://localhost:8080/api/carts/1/items' \
  -H 'Content-Type: application/json' \
  -d '{
    "productId": 1,
    "quantity": 2
  }'
```

**응답 (201 Created):**
```json
{
  "id": 1,
  "productId": 1,
  "productName": "iPhone 15 Pro",
  "price": 1500000,
  "quantity": 2,
  "totalPrice": 3000000
}
```

### 3. 주문 생성 (결제)

```bash
curl -X POST 'http://localhost:8080/api/orders' \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": 1,
    "userCouponId": 1,
    "idempotencyKey": "order-20251116-123456"
  }'
```

**응답 (201 Created):**
```json
{
  "id": 1,
  "orderNumber": "ORD-20251116-000001",
  "userId": 1,
  "items": [...],
  "totalAmount": 3000000,
  "discountAmount": 300000,
  "finalAmount": 2700000,
  "status": "PAID",
  "createdAt": "2025-11-16T12:00:00",
  "paidAt": "2025-11-16T12:00:05"
}
```

---

## 🔄 주요 비즈니스 플로우

### 주문 생성 플로우

```
1. 사용자가 장바구니에 상품 추가
   POST /api/carts/{userId}/items

2. 장바구니 확인
   GET /api/carts/{userId}

3. 쿠폰 조회 (선택)
   GET /api/users/{userId}/coupons

4. 주문 생성 (결제)
   POST /api/orders
   ① 장바구니 조회
   ② 재고 검증 및 차감 (Optimistic Lock)
   ③ 쿠폰 검증 및 할인 계산
   ④ 잔액 검증 및 차감 (Pessimistic Lock)
   ⑤ 주문 생성 (멱등성 키 체크)
   ⑥ 외부 시스템 연동 (비동기)
   ⑦ 장바구니 비우기

5. 주문 확인
   GET /api/orders/{orderId}
```

상세한 시퀀스 다이어그램은 [sequence-diagrams-mermaid.md](../design/sequence-diagrams-mermaid.md) 참조

---

## ⚠️ 에러 응답

### 공통 에러 형식

```json
{
  "timestamp": "2025-11-16T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "상품의 재고가 부족합니다.",
  "path": "/api/orders"
}
```

### 주요 에러 코드

| HTTP Status | Error Code | 설명 |
|------------|------------|------|
| 400 | `BAD_REQUEST` | 잘못된 요청 |
| 400 | `INSUFFICIENT_BALANCE` | 잔액 부족 |
| 400 | `PRODUCT_OUT_OF_STOCK` | 재고 부족 |
| 400 | `COUPON_EXHAUSTED` | 쿠폰 소진 |
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 403 | `FORBIDDEN` | 권한 없음 |
| 404 | `NOT_FOUND` | 리소스 없음 |
| 409 | `CONFLICT` | 리소스 충돌 |
| 409 | `COUPON_ALREADY_ISSUED` | 이미 발급된 쿠폰 |
| 409 | `IDEMPOTENCY_KEY_CONFLICT` | 멱등성 키 중복 |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 오류 |

---

## 🎯 특별한 기능

### 1. 멱등성 보장 (Idempotency)

주문 생성 API는 `idempotencyKey`를 사용하여 중복 주문을 방지합니다.

```json
{
  "userId": 1,
  "userCouponId": 1,
  "idempotencyKey": "order-20251116-123456"  // 고유한 키
}
```

동일한 키로 재요청 시 기존 주문 정보를 반환합니다.

### 2. 동시성 제어

| 도메인 | Lock 방식 | 이유 |
|--------|----------|------|
| **재고 관리** (Product) | Optimistic Lock (@Version) | 충돌 시 재시도, 성능 우선 |
| **쿠폰 발급** (Coupon) | Optimistic Lock (@Version) | 선착순 정확성, 높은 동시성 |
| **잔액 관리** (User) | Pessimistic Lock (SELECT FOR UPDATE) | 강한 일관성 필요 |
| **주문 번호 생성** (OrderSequence) | Pessimistic Lock (SELECT FOR UPDATE) | 주문 번호 중복 방지 |
| **주문 중복 방지** (Order) | 멱등성 키 (Idempotency Key) | 중복 결제 방지 |

### 3. 비동기 외부 연동

주문 생성 후 외부 시스템 전송은 비동기로 처리됩니다.
- **주문 완료**: 즉시 응답 (200 OK)
- **외부 전송**: 비동기 처리 (@Async)
- **실패 시**: 재시도 로직 (최대 3회)
- **영속화**: OutboundEvent 테이블에 저장

### 4. 페이징

목록 조회 API는 Spring Data JPA의 `Pageable`을 사용합니다.

```bash
GET /api/products?page=0&size=20&sort=price,desc
```

**응답 (Page<Product>):**
```json
{
  "content": [...],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 100,
  "totalPages": 5,
  "last": false,
  "first": true
}
```

---

## 🧪 테스트 방법

### 1. Swagger UI (권장)
```
http://localhost:8080/swagger-ui.html
```
- 모든 API를 브라우저에서 직접 테스트 가능
- Request/Response 스키마 자동 표시

### 2. cURL
```bash
# 상품 목록 조회
curl -X GET 'http://localhost:8080/api/products'

# 잔액 충전
curl -X POST 'http://localhost:8080/api/users/1/balance/charge' \
  -H 'Content-Type: application/json' \
  -d '{"amount": 10000}'
```

### 3. HTTPie
```bash
http GET http://localhost:8080/api/products
http POST http://localhost:8080/api/users/1/balance/charge amount:=10000
```

### 4. Postman
- Import → `openapi.yaml` 선택
- Collection 자동 생성

### 5. 통합 테스트 (TestContainers)
```bash
./gradlew test
```
- 260개 테스트 케이스 (242개 통과, 18개 스킵)
- MySQL 8.0 컨테이너 사용 (TestContainers)
- JaCoCo 코드 커버리지 ~85%
- 동시성 테스트 포함 (잔액, 재고, 쿠폰)

---

## 📊 데이터 모델

### 주요 엔티티

- **User**: 사용자 (id, email, name, balance, role, status)
- **Product**: 상품 (id, name, price, stock, category, status, version)
- **Cart**: 장바구니 (id, userId, items)
- **Order**: 주문 (id, orderNumber, userId, items, totalAmount, status)
- **OrderSequence**: 주문 번호 시퀀스 (orderDate, sequence) - ORD-YYYYMMDD-NNNNNN 생성
- **Payment**: 결제 (id, orderId, amount, method, status)
- **Coupon**: 쿠폰 (id, code, name, type, discountValue, version)

상세한 데이터 모델은 [domain-design.md](../design/domain-design.md) 참조

---

## 🔧 개발 환경 설정

### 필수 요구사항

- **Java**: 17
- **Spring Boot**: 3.5.7
- **MySQL**: 8.0
- **Gradle**: 8.14.3
- **Docker**: 필수 (MySQL 컨테이너용)

### 로컬 실행

```bash
# 1. MySQL 컨테이너 실행
docker-compose up -d

# 2. 애플리케이션 실행 (dev 프로파일 - 기본)
./gradlew bootRun

# 3. 또는 운영 환경으로 실행
./gradlew bootRun --args='--spring.profiles.active=prod'
```

### 주요 URL

| 서비스 | URL |
|--------|-----|
| **Swagger UI** | http://localhost:8080/swagger-ui.html |
| **OpenAPI Spec** | http://localhost:8080/api-docs |
| **Health Check** | http://localhost:8080/actuator/health |
| **MySQL** | localhost:3306 (root/123123) |

---

## 📝 버전 히스토리

| Version | Date | Changes |
|---------|------|---------|
| 3.0 | 2025-11-20 | MySQL 단일화 및 신규 기능 추가 (5주차)<br/>- InMemory Repository 완전 제거<br/>- OrderSequence 엔티티 추가 (주문 번호 생성)<br/>- Payment 엔티티 추가 (결제 정보 관리)<br/>- 테스트 260개로 증가 (242 통과, 18 스킵)<br/>- 동시성 테스트 강화 (잔액, 재고, 쿠폰) |
| 2.0 | 2025-11-16 | 실제 구현 반영 (4주차)<br/>- API 경로 `/api` 프리픽스 추가<br/>- 동시성 제어 방식 수정<br/>- H2 제거, MySQL 8.0만 사용<br/>- 통합 테스트 정보 추가<br/>- Use Case 매핑 추가 |
| 1.0 | 2025-10-28 | 초기 API 명세 작성 |

---

## 🔗 관련 문서

- **[테스트 가이드](../testing/TEST_GUIDE.md)** - 통합 테스트 작성 방법
- **[Repository 구현](../architecture/REPOSITORY_IMPLEMENTATION.md)** - Repository 패턴 및 동시성 제어
- **[도메인 설계](../design/domain-design.md)** - 엔티티 및 비즈니스 로직
- **[Use Cases](../requirements/use-cases.md)** - 상세 유스케이스 명세

---

## 📜 라이선스

Apache 2.0 License

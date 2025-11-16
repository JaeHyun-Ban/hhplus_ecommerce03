# E-Commerce API Documentation

> **프로젝트 전체 정보**: [메인 README](../../README.md) | [문서 가이드](../README.md)

---

## 📚 문서 목차

| 문서 | 설명 | 링크 |
|-----|------|------|
| **OpenAPI Spec** | Swagger/OpenAPI 3.0 명세서 | [openapi.yaml](../../../../무제%20폴더%202/assignment/openapi.yaml) |
| **Swagger Guide** | Swagger UI 사용 가이드 | [SWAGGER_GUIDE.md](SWAGGER_GUIDE.md) |
| **RESTful API** | API 엔드포인트 상세 설명 | [restful-api-endpoints.md](restful-api-endpoints.md) |
| **Sequence Diagrams** | 비즈니스 플로우 시퀀스 다이어그램 | [sequence-diagrams.md](sequence-diagrams.md) |
| **Domain Design** | 도메인 및 엔티티 설계 | [domain-design.md](domain-design.md) |
| **Data Models** | 데이터 모델 명세 | [data-models.md](data-models.md) |
| **User Stories** | 사용자 스토리 | [user-stories.md](user-stories.md) |
| **Requirements** | 요구사항 명세 | [requirements.md](requirements.md) |

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
- **Base URL (Dev):** `https://dev-api.ecommerce.com`
- **Base URL (Prod):** `https://api.ecommerce.com`
- **API Version:** v1
- **Authentication:** JWT Bearer Token

### 지원하는 기능

| 도메인 | 기능 | 엔드포인트 |
|-------|------|----------|
| **Users** | 사용자 관리, 잔액 충전 | `/users/*` |
| **Products** | 상품 조회, 인기 상품, 재입고 알림 | `/products/*` |
| **Cart** | 장바구니 관리 | `/carts/*` |
| **Orders** | 주문 생성, 결제, 취소 | `/orders/*` |
| **Coupons** | 쿠폰 발급, 조회 | `/coupons/*` |

---

## 🔑 인증

### JWT Bearer Token

모든 API는 JWT 토큰 인증이 필요합니다 (일부 공개 API 제외).

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Swagger UI에서 인증

1. 우측 상단 "Authorize" 버튼 클릭
2. Bearer Token 입력
3. Authorize 클릭

---

## 📋 주요 API 엔드포인트

### 사용자 (Users)

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/users` | 사용자 등록 |
| GET | `/users/{userId}` | 사용자 조회 |
| GET | `/users/{userId}/balance` | 잔액 조회 |
| POST | `/users/{userId}/balance/charge` | 잔액 충전 |
| GET | `/users/{userId}/balance/history` | 잔액 이력 조회 |

### 상품 (Products)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/products` | 상품 목록 조회 |
| GET | `/products/{productId}` | 상품 상세 조회 |
| GET | `/products/popular` | <br/>인기 상품 조회 (Top 5) |
| POST | `/products/{productId}/restock-notifications` | 재입고 알림 신청 |
| GET | `/categories` | 카테고리 목록 |

### 장바구니 (Cart)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/carts/{userId}` | 장바구니 조회 |
| POST | `/carts/{userId}/items` | 상품 추가 |
| PATCH | `/carts/{userId}/items/{itemId}` | 수량 변경 |
| DELETE | `/carts/{userId}/items/{itemId}` | 상품 삭제 |
| DELETE | `/carts/{userId}/items` | 장바구니 비우기 |

### 주문 (Orders)

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/orders` | 주문 생성 (결제) |
| GET | `/orders` | 주문 목록 조회 |
| GET | `/orders/{orderId}` | 주문 상세 조회 |
| POST | `/orders/{orderId}/cancel` | 주문 취소 |

### 쿠폰 (Coupons)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/coupons` | 쿠폰 목록 조회 |
| POST | `/coupons/{couponId}/issue` | 쿠폰 발급 |
| GET | `/users/{userId}/coupons` | 보유 쿠폰 조회 |

---

## 💡 API 사용 예시

### 1. 잔액 충전

```bash
curl -X POST 'http://localhost:8080/users/1/balance/charge' \
  -H 'Content-Type: application/json' \
  -d '{
    "amount": 10000
  }'
```

**응답:**
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "transactionType": "CHARGE",
    "amount": 10000,
    "balanceBefore": 50000,
    "balanceAfter": 60000,
    "createdAt": "2025-10-28T12:30:00"
  }
}
```

### 2. 장바구니에 상품 추가

```bash
curl -X POST 'http://localhost:8080/carts/1/items' \
  -H 'Content-Type: application/json' \
  -d '{
    "productId": 1,
    "quantity": 2
  }'
```

### 3. 주문 생성 (결제)

```bash
curl -X POST 'http://localhost:8080/orders' \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": 1,
    "items": [
      {
        "productId": 1,
        "quantity": 2
      }
    ],
    "userCouponIds": [1],
    "idempotencyKey": "order-20251028-123456"
  }'
```

**응답:**
```json
{
  "success": true,
  "data": {
    "orderId": 1,
    "orderNumber": "ORD-20251028-000001",
    "userId": 1,
    "totalAmount": 20000,
    "discountAmount": 2000,
    "finalAmount": 18000,
    "status": "PAID",
    "orderedAt": "2025-10-28T12:00:00",
    "paidAt": "2025-10-28T12:00:05"
  }
}
```

---

## 🔄 주요 비즈니스 플로우

### 주문 생성 플로우

```
1. 사용자가 장바구니에 상품 추가
   POST /carts/{userId}/items

2. 장바구니 확인
   GET /carts/{userId}

3. 쿠폰 조회 (선택)
   GET /users/{userId}/coupons

4. 주문 생성 (결제)
   POST /orders
   - 재고 차감
   - 잔액 차감
   - 쿠폰 사용
   - 주문 생성
   - 외부 시스템 연동 (비동기)

5. 주문 확인
   GET /orders/{orderId}
```

상세한 시퀀스 다이어그램은 [sequence-diagrams.md](sequence-diagrams.md) 참조

---

## ⚠️ 에러 응답

### 공통 에러 형식

```json
{
  "success": false,
  "error": {
    "code": "PRODUCT_OUT_OF_STOCK",
    "message": "상품의 재고가 부족합니다.",
    "details": {
      "productId": 1,
      "requestedQuantity": 10,
      "availableStock": 5
    }
  },
  "timestamp": "2025-10-28T12:00:00"
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
  "items": [...],
  "idempotencyKey": "order-20251028-123456"  // 고유한 키
}
```

동일한 키로 재요청 시 기존 주문 정보를 반환합니다.

### 2. 동시성 제어

- **재고 관리**: Optimistic Lock (@Version)
- **쿠폰 발급**: Pessimistic Lock (SELECT FOR UPDATE)
- **잔액 관리**: Pessimistic Lock

### 3. 비동기 외부 연동

주문 생성 후 외부 시스템 전송은 비동기로 처리됩니다.
- 주문은 정상 완료
- 외부 전송 실패 시 재시도
- 최대 재시도 초과 시 Dead Letter Queue

### 4. 페이징

목록 조회 API는 페이징을 지원합니다.

```
GET /products?page=0&size=20
```

**응답:**
```json
{
  "success": true,
  "data": {
    "content": [...],
    "page": 0,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5
  }
}
```

---

## 🧪 테스트 방법

### 1. Swagger UI
```
http://localhost:8080/swagger-ui.html
```

### 2. cURL
```bash
curl -X GET 'http://localhost:8080/products'
```

### 3. HTTPie
```bash
http GET http://localhost:8080/products
```

### 4. Postman
- Import → `openapi.yaml` 선택
- Collection 자동 생성

---

## 📊 데이터 모델

### 주요 엔티티

- **User**: 사용자 (id, email, name, balance, role, status)
- **Product**: 상품 (id, name, price, stock, category, status)
- **Cart**: 장바구니 (id, userId, items)
- **Order**: 주문 (id, orderNumber, userId, items, totalAmount, status)
- **Coupon**: 쿠폰 (id, code, name, type, discountValue)

상세한 데이터 모델은 [domain-design.md](domain-design.md) 참조

---

## 🔧 개발 환경 설정

### 필수 요구사항

- Java 17
- Spring Boot 3.5.7
- MySQL 8.0 (또는 H2)
- Gradle 8.14.3

### 로컬 실행

```bash
# H2 인메모리 DB로 실행 (기본)
./gradlew bootRun

# MySQL로 실행 (Docker)
docker-compose up -d
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### 주요 URL

| 서비스 | URL |
|--------|-----|
| **Swagger UI** | http://localhost:8080/swagger-ui.html |
| **OpenAPI Spec** | http://localhost:8080/api-docs |
| **H2 Console** | http://localhost:8080/h2-console |
| **Health Check** | http://localhost:8080/actuator/health |

---

## 📝 버전 히스토리

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-10-28 | Initial release |

---

## 🤝 기여하기

API 개선 제안이나 버그 리포트는 이슈로 등록해주세요.

---

## 📞 연락처

- API Support: support@ecommerce.com
- Documentation: https://docs.ecommerce.com

---

## 📜 라이선스

Apache 2.0 License

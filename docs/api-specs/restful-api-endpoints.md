# RESTful API 엔드포인트 설계

> **Last Updated**: 2025-11-16
> **Version**: 2.0 (4주차 - 실제 구현 반영)

## 목차
1. [User API](#1-user-api)
2. [Product API](#2-product-api)
3. [Cart API](#3-cart-api)
4. [Order API](#4-order-api)
5. [Coupon API](#5-coupon-api)
6. [공통 응답 형식](#6-공통-응답-형식)
7. [RESTful API 설계 원칙](#7-restful-api-설계-원칙)

---

## 1. User API

### 1.1 사용자 등록
```
POST /api/users
```

**Use Case**: UC-002

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "name": "홍길동"
}
```

**Response:** `201 Created`
```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "balance": 0,
  "role": "USER",
  "status": "ACTIVE",
  "createdAt": "2025-11-16T12:00:00"
}
```

### 1.2 사용자 조회
```
GET /api/users/{userId}
```

**Use Case**: UC-003

**Response:** `200 OK`
```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "balance": 50000,
  "role": "USER",
  "status": "ACTIVE",
  "createdAt": "2025-11-16T12:00:00"
}
```

### 1.3 잔액 충전
```
POST /api/users/{userId}/balance/charge
```

**Use Case**: UC-001

**Request Body:**
```json
{
  "amount": 10000
}
```

**Response:** `200 OK`
```json
{
  "id": 1,
  "userId": 1,
  "transactionType": "CHARGE",
  "amount": 10000,
  "balanceBefore": 50000,
  "balanceAfter": 60000,
  "description": "잔액 충전",
  "createdAt": "2025-11-16T12:30:00"
}
```

**동시성 제어**: Pessimistic Lock (SELECT FOR UPDATE)

### 1.4 잔액 조회
```
GET /api/users/{userId}/balance
```

**Use Case**: UC-004

**Response:** `200 OK`
```json
{
  "userId": 1,
  "balance": 60000
}
```

### 1.5 잔액 이력 조회
```
GET /api/users/{userId}/balance/history?page=0&size=10
```

**Use Case**: UC-005

**Response:** `200 OK` (Page<BalanceHistory>)
```json
{
  "content": [
    {
      "id": 1,
      "userId": 1,
      "transactionType": "CHARGE",
      "amount": 10000,
      "balanceBefore": 50000,
      "balanceAfter": 60000,
      "description": "잔액 충전",
      "createdAt": "2025-11-16T12:30:00"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10
  },
  "totalElements": 1,
  "totalPages": 1,
  "last": true,
  "first": true
}
```

---

## 2. Product API

### 2.1 상품 목록 조회
```
GET /api/products?page=0&size=20
```

**Use Case**: UC-003

**Response:** `200 OK` (Page<Product>)
```json
{
  "content": [
    {
      "id": 1,
      "name": "iPhone 15 Pro",
      "description": "최신 아이폰",
      "price": 1500000,
      "stock": 100,
      "safetyStock": 10,
      "category": {
        "id": 1,
        "name": "전자기기"
      },
      "status": "AVAILABLE",
      "version": 0,
      "createdAt": "2025-11-16T10:00:00",
      "updatedAt": "2025-11-16T10:00:00"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 1,
  "totalPages": 1
}
```

### 2.2 카테고리별 상품 조회
```
GET /api/products?categoryId={categoryId}&page=0&size=20
```

**Use Case**: UC-003

**Response:** `200 OK` (Page<Product>)

### 2.3 상품 상세 조회
```
GET /api/products/{productId}
```

**Use Case**: UC-004

**Response:** `200 OK`
```json
{
  "id": 1,
  "name": "iPhone 15 Pro",
  "description": "최신 아이폰",
  "price": 1500000,
  "stock": 100,
  "safetyStock": 10,
  "category": {
    "id": 1,
    "name": "전자기기",
    "description": "전자기기 카테고리"
  },
  "status": "AVAILABLE",
  "version": 0,
  "createdAt": "2025-11-16T10:00:00",
  "updatedAt": "2025-11-16T10:00:00"
}
```

### 2.4 인기 상품 조회 (최근 3일, Top 5)
```
GET /api/products/popular?days=3&limit=5
```

**Use Case**: UC-006

**Response:** `200 OK`
```json
[
  {
    "productId": 1,
    "productName": "iPhone 15 Pro",
    "price": 1500000,
    "totalSalesCount": 150,
    "totalSalesAmount": 225000000,
    "rank": 1
  },
  {
    "productId": 2,
    "productName": "Galaxy S24",
    "price": 1300000,
    "totalSalesCount": 120,
    "totalSalesAmount": 156000000,
    "rank": 2
  }
]
```

### 2.5 카테고리 목록 조회
```
GET /api/categories
```

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "name": "전자기기",
    "description": "전자기기 카테고리"
  },
  {
    "id": 2,
    "name": "의류",
    "description": "의류 카테고리"
  }
]
```

---

## 3. Cart API

### 3.1 장바구니 조회
```
GET /api/carts/{userId}
```

**Use Case**: UC-007

**Response:** `200 OK`
```json
{
  "id": 1,
  "userId": 1,
  "items": [
    {
      "id": 1,
      "product": {
        "id": 1,
        "name": "iPhone 15 Pro",
        "price": 1500000,
        "stock": 100
      },
      "quantity": 2,
      "priceAtAdd": 1500000,
      "createdAt": "2025-11-16T10:00:00"
    }
  ],
  "createdAt": "2025-11-16T10:00:00",
  "updatedAt": "2025-11-16T11:00:00"
}
```

### 3.2 장바구니에 상품 추가
```
POST /api/carts/{userId}/items
```

**Use Case**: UC-008

**Request Body:**
```json
{
  "productId": 1,
  "quantity": 2
}
```

**Response:** `201 Created`
```json
{
  "id": 1,
  "product": {
    "id": 1,
    "name": "iPhone 15 Pro",
    "price": 1500000
  },
  "quantity": 2,
  "priceAtAdd": 1500000,
  "createdAt": "2025-11-16T11:00:00"
}
```

### 3.3 장바구니 상품 수량 변경
```
PUT /api/carts/items/{cartItemId}
```

**Use Case**: UC-009

**Request Body:**
```json
{
  "quantity": 3
}
```

**Response:** `200 OK`
```json
{
  "id": 1,
  "product": {
    "id": 1,
    "name": "iPhone 15 Pro",
    "price": 1500000
  },
  "quantity": 3,
  "priceAtAdd": 1500000
}
```

### 3.4 장바구니 상품 삭제
```
DELETE /api/carts/items/{cartItemId}
```

**Use Case**: UC-010

**Response:** `204 No Content`

### 3.5 장바구니 비우기
```
DELETE /api/carts/{userId}/items
```

**Use Case**: UC-011

**Response:** `204 No Content`

---

## 4. Order API

### 4.1 주문 생성 (결제)
```
POST /api/orders
```

**Use Case**: UC-012

**플로우**:
1. 장바구니 조회
2. 재고 검증 및 차감 (Optimistic Lock)
3. 쿠폰 검증 및 할인 계산
4. 잔액 검증 및 차감 (Pessimistic Lock)
5. 주문 생성 (멱등성 키 체크)
6. 외부 시스템 연동 (비동기)
7. 장바구니 비우기

**Request Body:**
```json
{
  "userId": 1,
  "userCouponId": 1,
  "idempotencyKey": "order-20251116-123456"
}
```

**Response:** `201 Created`
```json
{
  "id": 1,
  "orderNumber": "ORD-20251116-000001",
  "user": {
    "id": 1,
    "name": "홍길동"
  },
  "orderItems": [
    {
      "id": 1,
      "product": {
        "id": 1,
        "name": "iPhone 15 Pro"
      },
      "quantity": 2,
      "priceAtOrder": 1500000,
      "subtotal": 3000000
    }
  ],
  "totalAmount": 3000000,
  "discountAmount": 300000,
  "finalAmount": 2700000,
  "status": "PAID",
  "idempotencyKey": "order-20251116-123456",
  "createdAt": "2025-11-16T12:00:00",
  "paidAt": "2025-11-16T12:00:05"
}
```

**동시성 제어**:
- 재고 차감: Optimistic Lock (@Version)
- 잔액 차감: Pessimistic Lock (SELECT FOR UPDATE)
- 중복 방지: 멱등성 키 (Unique Constraint)

### 4.2 주문 상세 조회
```
GET /api/orders/{orderId}
```

**Use Case**: UC-013

**Response:** `200 OK`
```json
{
  "id": 1,
  "orderNumber": "ORD-20251116-000001",
  "user": {
    "id": 1,
    "email": "user@example.com",
    "name": "홍길동"
  },
  "orderItems": [
    {
      "id": 1,
      "product": {
        "id": 1,
        "name": "iPhone 15 Pro"
      },
      "quantity": 2,
      "priceAtOrder": 1500000,
      "subtotal": 3000000
    }
  ],
  "totalAmount": 3000000,
  "discountAmount": 300000,
  "finalAmount": 2700000,
  "usedCoupons": [
    {
      "id": 1,
      "couponName": "10% 할인 쿠폰",
      "discountAmount": 300000
    }
  ],
  "status": "PAID",
  "createdAt": "2025-11-16T12:00:00",
  "paidAt": "2025-11-16T12:00:05"
}
```

### 4.3 주문 번호로 조회
```
GET /api/orders/order-number/{orderNumber}
```

**Use Case**: UC-013

**Response:** `200 OK` (동일한 Order 엔티티)

### 4.4 사용자별 주문 목록 조회
```
GET /api/orders?userId={userId}&page=0&size=10
```

**Use Case**: UC-014

**Response:** `200 OK` (Page<Order>)
```json
{
  "content": [
    {
      "id": 1,
      "orderNumber": "ORD-20251116-000001",
      "totalAmount": 3000000,
      "discountAmount": 300000,
      "finalAmount": 2700000,
      "status": "PAID",
      "createdAt": "2025-11-16T12:00:00"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10
  },
  "totalElements": 1,
  "totalPages": 1
}
```

### 4.5 주문 취소
```
POST /api/orders/{orderId}/cancel
```

**Use Case**: UC-015

**Request Body:**
```json
{
  "reason": "단순 변심"
}
```

**Response:** `200 OK`
```json
{
  "id": 1,
  "orderNumber": "ORD-20251116-000001",
  "status": "CANCELLED",
  "cancelledAt": "2025-11-16T13:00:00",
  "cancelReason": "단순 변심"
}
```

---

## 5. Coupon API

### 5.1 발급 가능한 쿠폰 목록 조회
```
GET /api/coupons
```

**Use Case**: UC-018

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "code": "WELCOME2025",
    "name": "신규 가입 쿠폰",
    "description": "신규 가입 고객 10% 할인",
    "discountType": "PERCENTAGE",
    "discountValue": 10,
    "minimumOrderAmount": 10000,
    "maximumDiscountAmount": 5000,
    "totalQuantity": 1000,
    "issuedQuantity": 150,
    "remainingQuantity": 850,
    "status": "ACTIVE",
    "issueStartAt": "2025-10-01T00:00:00",
    "issueEndAt": "2025-12-31T23:59:59",
    "validFrom": "2025-10-01T00:00:00",
    "validUntil": "2025-12-31T23:59:59",
    "version": 5
  }
]
```

### 5.2 쿠폰 발급 (선착순)
```
POST /api/coupons/{couponId}/issue
```

**Use Case**: UC-017

**Request Body:**
```json
{
  "userId": 1
}
```

**Response:** `200 OK`
```json
{
  "userCouponId": 1,
  "couponId": 1,
  "couponName": "신규 가입 쿠폰",
  "discountType": "PERCENTAGE",
  "discountValue": 10,
  "status": "ISSUED",
  "issuedAt": "2025-11-16T12:00:00",
  "validFrom": "2025-10-01T00:00:00",
  "validUntil": "2025-12-31T23:59:59"
}
```

**동시성 제어**: Optimistic Lock (@Version) - 선착순 정확성 보장

**에러 응답**:
```json
{
  "timestamp": "2025-11-16T12:00:00",
  "status": 409,
  "error": "Conflict",
  "message": "쿠폰이 모두 소진되었습니다.",
  "path": "/api/coupons/1/issue"
}
```

### 5.3 사용자 보유 쿠폰 조회
```
GET /api/users/{userId}/coupons?status=ISSUED
```

**Use Case**: UC-019

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "coupon": {
      "id": 1,
      "code": "WELCOME2025",
      "name": "신규 가입 쿠폰",
      "discountType": "PERCENTAGE",
      "discountValue": 10,
      "minimumOrderAmount": 10000,
      "maximumDiscountAmount": 5000
    },
    "status": "ISSUED",
    "issuedAt": "2025-11-16T12:00:00",
    "usedAt": null,
    "validFrom": "2025-10-01T00:00:00",
    "validUntil": "2025-12-31T23:59:59"
  }
]
```

---

## 6. 공통 응답 형식

### 6.1 성공 응답

일반적으로 **엔티티 또는 DTO를 직접 반환**합니다.

```json
{
  "id": 1,
  "name": "홍길동",
  "balance": 50000,
  "createdAt": "2025-11-16T12:00:00"
}
```

페이징이 필요한 경우 `Page<T>` 형식:

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

### 6.2 에러 응답

Spring Boot 기본 에러 응답 형식:

```json
{
  "timestamp": "2025-11-16T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "상품의 재고가 부족합니다.",
  "path": "/api/orders"
}
```

### 6.3 주요 에러 코드

| HTTP Status | 에러 메시지 | 상황 |
|------------|------------|------|
| 400 | Bad Request | 잘못된 요청 (검증 실패) |
| 400 | 잔액이 부족합니다 | 잔액 부족 |
| 400 | 상품의 재고가 부족합니다 | 재고 부족 |
| 400 | 쿠폰이 모두 소진되었습니다 | 쿠폰 소진 |
| 401 | Unauthorized | 인증 실패 |
| 403 | Forbidden | 권한 없음 |
| 404 | Not Found | 리소스를 찾을 수 없음 |
| 409 | Conflict | 리소스 충돌 (중복, 동시성 충돌) |
| 409 | 이미 발급된 쿠폰입니다 | 쿠폰 중복 발급 |
| 409 | Optimistic Lock Exception | 동시성 충돌 (재시도 필요) |
| 500 | Internal Server Error | 서버 내부 오류 |

---

## 7. RESTful API 설계 원칙

### 7.1 리소스 중심 설계
- **Collection**: `/api/products` (복수형)
- **Item**: `/api/products/{productId}` (단수 ID)
- **Sub-resource**: `/api/users/{userId}/balance/charge`

### 7.2 HTTP 메서드 활용
- `GET`: 조회 (안전성, 멱등성 보장)
- `POST`: 생성 또는 액션 (멱등성 보장 안됨)
- `PUT`: 전체 수정 (멱등성 보장)
- `PATCH`: 부분 수정
- `DELETE`: 삭제 (멱등성 보장)

### 7.3 상태 코드 활용
- `200 OK`: 성공
- `201 Created`: 생성 성공
- `204 No Content`: 성공 (응답 본문 없음)
- `400 Bad Request`: 잘못된 요청
- `401 Unauthorized`: 인증 실패
- `403 Forbidden`: 권한 없음
- `404 Not Found`: 리소스 없음
- `409 Conflict`: 충돌 (중복, 동시성)
- `500 Internal Server Error`: 서버 오류

### 7.4 API 경로 구조
```
/api/{resource}                    # Collection 조회/생성
/api/{resource}/{id}               # Item 조회/수정/삭제
/api/{resource}/{id}/{action}      # 특정 액션 수행
/api/{resource}/{id}/{sub-resource}  # Sub-resource 접근
```

**예시**:
```
GET    /api/products               # 상품 목록 조회
GET    /api/products/1             # 상품 1 조회
GET    /api/products/popular       # 인기 상품 조회 (액션)
POST   /api/users/1/balance/charge # 잔액 충전 (액션)
GET    /api/users/1/coupons        # 사용자 1의 쿠폰 조회
```

### 7.5 페이징 및 정렬
- **Query Parameter** 사용: `?page=0&size=20&sort=price,desc`
- **Spring Data JPA Pageable** 활용
- 응답에 페이징 정보 포함 (`totalElements`, `totalPages` 등)

### 7.6 필터링
- Query Parameter 사용: `?categoryId=1&status=ACTIVE`
- 여러 필터 조합 가능

### 7.7 중첩 리소스
- 관계를 명확히 표현: `/api/users/{userId}/coupons`
- 깊이는 2단계까지 권장 (가독성)

---

## 8. 보안 및 동시성

### 8.1 인증/인가
- ⚠️ **현재 미구현** (향후 JWT Bearer Token 예정)
- Authorization Header: `Bearer {token}`

### 8.2 입력 검증
- `@Valid` 어노테이션으로 Request Body 검증
- Bean Validation 사용 (`@NotNull`, `@Min`, `@Max` 등)

### 8.3 동시성 제어

| 도메인 | Lock 방식 | 위치 |
|--------|----------|------|
| **User (잔액)** | Pessimistic Lock | `UserRepository.findByIdWithLock()` |
| **Product (재고)** | Optimistic Lock | `@Version` 필드 |
| **Coupon (선착순)** | Optimistic Lock | `@Version` 필드 |
| **Order (중복 방지)** | 멱등성 키 | `idempotencyKey` Unique Constraint |

### 8.4 멱등성 보장

**주문 생성 API**:
- `idempotencyKey` 필수
- 동일한 키로 중복 요청 시 기존 주문 반환
- DB Unique Constraint로 보장

**쿠폰 발급 API**:
- 동일 사용자가 동일 쿠폰 중복 발급 방지
- `(userId, couponId)` Unique Constraint

---

## 9. 비동기 처리

### 9.1 외부 시스템 연동
- 주문 생성 후 외부 시스템 전송은 **비동기**로 처리 (`@Async`)
- 주문 생성 API는 즉시 응답 (200 OK)
- 전송 실패 시 재시도 메커니즘 (최대 3회)
- `OutboundEvent` 테이블에 이벤트 영속화

### 9.2 처리 순서
```
1. 주문 생성 (동기) ──> 200 OK 즉시 응답
2. OutboundEvent 저장 (동기)
3. 외부 시스템 전송 (비동기) ──> @Async
   - 성공: SENT 상태로 변경
   - 실패: 재시도 (최대 3회)
```

---

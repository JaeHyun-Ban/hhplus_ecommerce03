# RESTful API 엔드포인트 설계

## 목차
1. [User API](#1-user-api)
2. [Product API](#2-product-api)
3. [Cart API](#3-cart-api)
4. [Order API](#4-order-api)
5. [Coupon API](#5-coupon-api)
6. [공통 응답 형식](#6-공통-응답-형식)

---

## 1. User API

### 1.1 사용자 등록
```
POST /users
```

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
  "success": true,
  "data": {
    "id": 1,
    "email": "user@example.com",
    "name": "홍길동",
    "balance": 0,
    "role": "USER",
    "status": "ACTIVE",
    "createdAt": "2025-10-28T12:00:00"
  }
}
```

### 1.2 사용자 조회
```
GET /users/{userId}
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "user@example.com",
    "name": "홍길동",
    "balance": 50000,
    "role": "USER",
    "status": "ACTIVE",
    "createdAt": "2025-10-28T12:00:00"
  }
}
```

### 1.3 잔액 충전
```
POST /users/{userId}/balance/charge
```

**Request Body:**
```json
{
  "amount": 10000
}
```

**Response:** `200 OK`
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

### 1.4 잔액 조회
```
GET /users/{userId}/balance
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "balance": 60000
  }
}
```

### 1.5 잔액 이력 조회
```
GET /users/{userId}/balance/history?page=0&size=10
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "type": "CHARGE",
        "amount": 10000,
        "balanceBefore": 50000,
        "balanceAfter": 60000,
        "description": "잔액 충전",
        "createdAt": "2025-10-28T12:30:00"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

## 2. Product API

### 2.1 상품 목록 조회
```
GET /products?categoryId={categoryId}&page=0&size=20
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "name": "상품1",
        "description": "상품 설명",
        "price": 10000,
        "stock": 100,
        "categoryId": 1,
        "categoryName": "전자기기",
        "status": "AVAILABLE"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### 2.2 상품 상세 조회
```
GET /products/{productId}
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "상품1",
    "description": "상품 설명",
    "price": 10000,
    "stock": 100,
    "safetyStock": 10,
    "category": {
      "id": 1,
      "name": "전자기기"
    },
    "status": "AVAILABLE",
    "createdAt": "2025-10-28T10:00:00",
    "updatedAt": "2025-10-28T10:00:00"
  }
}
```

### 2.3 인기 상품 조회 (최근 3일, Top 5)
```
GET /products/popular?days=3&limit=5
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": [
    {
      "productId": 1,
      "productName": "상품1",
      "price": 10000,
      "totalSalesCount": 150,
      "totalSalesAmount": 1500000,
      "rank": 1
    },
    {
      "productId": 2,
      "productName": "상품2",
      "price": 20000,
      "totalSalesCount": 120,
      "totalSalesAmount": 2400000,
      "rank": 2
    }
  ]
}
```

### 2.4 재입고 알림 신청
```
POST /products/{productId}/restock-notifications
```

**Request Body:**
```json
{
  "userId": 1
}
```

**Response:** `201 Created`
```json
{
  "success": true,
  "data": {
    "id": 1,
    "userId": 1,
    "productId": 1,
    "status": "PENDING",
    "requestedAt": "2025-10-28T12:00:00"
  }
}
```

### 2.5 카테고리 목록 조회
```
GET /categories
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": [
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
}
```

---

## 3. Cart API

### 3.1 장바구니 조회
```
GET /carts/{userId}
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "cartId": 1,
    "userId": 1,
    "items": [
      {
        "id": 1,
        "productId": 1,
        "productName": "상품1",
        "price": 10000,
        "priceAtAdd": 10000,
        "quantity": 2,
        "subtotal": 20000,
        "isPriceChanged": false
      }
    ],
    "totalAmount": 20000,
    "createdAt": "2025-10-28T10:00:00",
    "updatedAt": "2025-10-28T11:00:00"
  }
}
```

### 3.2 장바구니에 상품 추가
```
POST /carts/{userId}/items
```

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
  "success": true,
  "data": {
    "id": 1,
    "productId": 1,
    "productName": "상품1",
    "price": 10000,
    "priceAtAdd": 10000,
    "quantity": 2,
    "subtotal": 20000
  }
}
```

### 3.3 장바구니 상품 수량 변경
```
PATCH /carts/{userId}/items/{itemId}
```

**Request Body:**
```json
{
  "quantity": 3
}
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "id": 1,
    "productId": 1,
    "productName": "상품1",
    "quantity": 3,
    "subtotal": 30000
  }
}
```

### 3.4 장바구니 상품 삭제
```
DELETE /carts/{userId}/items/{itemId}
```

**Response:** `204 No Content`

### 3.5 장바구니 비우기
```
DELETE /carts/{userId}/items
```

**Response:** `204 No Content`

---

## 4. Order API

### 4.1 주문 생성 (결제)
```
POST /orders
```

**Request Body:**
```json
{
  "userId": 1,
  "items": [
    {
      "productId": 1,
      "quantity": 2
    }
  ],
  "userCouponIds": [1],
  "idempotencyKey": "unique-key-12345"
}
```

**Response:** `201 Created`
```json
{
  "success": true,
  "data": {
    "orderId": 1,
    "orderNumber": "ORD-20251028-000001",
    "userId": 1,
    "items": [
      {
        "productId": 1,
        "productName": "상품1",
        "price": 10000,
        "quantity": 2,
        "subtotal": 20000
      }
    ],
    "totalAmount": 20000,
    "discountAmount": 2000,
    "finalAmount": 18000,
    "status": "PAID",
    "orderedAt": "2025-10-28T12:00:00",
    "paidAt": "2025-10-28T12:00:05"
  }
}
```

### 4.2 주문 상세 조회
```
GET /orders/{orderId}
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "orderId": 1,
    "orderNumber": "ORD-20251028-000001",
    "userId": 1,
    "userName": "홍길동",
    "items": [
      {
        "productId": 1,
        "productName": "상품1",
        "price": 10000,
        "quantity": 2,
        "subtotal": 20000
      }
    ],
    "totalAmount": 20000,
    "discountAmount": 2000,
    "finalAmount": 18000,
    "coupons": [
      {
        "userCouponId": 1,
        "couponName": "10% 할인 쿠폰",
        "discountAmount": 2000
      }
    ],
    "payment": {
      "paymentId": 1,
      "method": "BALANCE",
      "amount": 18000,
      "status": "COMPLETED"
    },
    "status": "PAID",
    "orderedAt": "2025-10-28T12:00:00",
    "paidAt": "2025-10-28T12:00:05"
  }
}
```

### 4.3 사용자별 주문 목록 조회
```
GET /orders?userId={userId}&page=0&size=10
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "orderId": 1,
        "orderNumber": "ORD-20251028-000001",
        "totalAmount": 20000,
        "discountAmount": 2000,
        "finalAmount": 18000,
        "status": "PAID",
        "orderedAt": "2025-10-28T12:00:00"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### 4.4 주문 취소
```
POST /orders/{orderId}/cancel
```

**Request Body:**
```json
{
  "reason": "단순 변심"
}
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "orderId": 1,
    "orderNumber": "ORD-20251028-000001",
    "status": "CANCELLED",
    "cancelledAt": "2025-10-28T13:00:00",
    "cancellationReason": "단순 변심"
  }
}
```

---

## 5. Coupon API

### 5.1 쿠폰 목록 조회
```
GET /coupons?page=0&size=20
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "code": "WELCOME2025",
        "name": "신규 가입 쿠폰",
        "description": "신규 가입 고객 10% 할인",
        "type": "PERCENTAGE",
        "discountValue": 10,
        "minimumOrderAmount": 10000,
        "maximumDiscountAmount": 5000,
        "totalQuantity": 1000,
        "remainingQuantity": 850,
        "status": "ACTIVE",
        "issueStartAt": "2025-10-01T00:00:00",
        "issueEndAt": "2025-12-31T23:59:59",
        "validFrom": "2025-10-01T00:00:00",
        "validUntil": "2025-12-31T23:59:59"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### 5.2 쿠폰 발급
```
POST /coupons/{couponId}/issue
```

**Request Body:**
```json
{
  "userId": 1
}
```

**Response:** `201 Created`
```json
{
  "success": true,
  "data": {
    "userCouponId": 1,
    "userId": 1,
    "couponId": 1,
    "couponName": "신규 가입 쿠폰",
    "status": "ISSUED",
    "issuedAt": "2025-10-28T12:00:00"
  }
}
```

### 5.3 사용자 보유 쿠폰 조회
```
GET /users/{userId}/coupons?status=ISSUED
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": [
    {
      "userCouponId": 1,
      "coupon": {
        "id": 1,
        "code": "WELCOME2025",
        "name": "신규 가입 쿠폰",
        "type": "PERCENTAGE",
        "discountValue": 10,
        "minimumOrderAmount": 10000,
        "maximumDiscountAmount": 5000
      },
      "status": "ISSUED",
      "issuedAt": "2025-10-28T12:00:00",
      "canUse": true
    }
  ]
}
```

---

## 6. 공통 응답 형식

### 6.1 성공 응답
```json
{
  "success": true,
  "data": { ... },
  "timestamp": "2025-10-28T12:00:00"
}
```

### 6.2 에러 응답
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

### 6.3 주요 에러 코드

| 에러 코드 | HTTP 상태 | 설명 |
|---------|---------|-----|
| `BAD_REQUEST` | 400 | 잘못된 요청 |
| `UNAUTHORIZED` | 401 | 인증 실패 |
| `FORBIDDEN` | 403 | 권한 없음 |
| `NOT_FOUND` | 404 | 리소스를 찾을 수 없음 |
| `CONFLICT` | 409 | 리소스 충돌 (중복 등) |
| `INSUFFICIENT_BALANCE` | 400 | 잔액 부족 |
| `PRODUCT_OUT_OF_STOCK` | 400 | 재고 부족 |
| `COUPON_EXHAUSTED` | 400 | 쿠폰 소진 |
| `COUPON_ALREADY_ISSUED` | 409 | 이미 발급된 쿠폰 |
| `ORDER_ALREADY_PAID` | 409 | 이미 결제된 주문 |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | 멱등성 키 중복 |
| `INTERNAL_SERVER_ERROR` | 500 | 서버 내부 오류 |

---

## 7. RESTful API 설계 원칙

### 7.1 리소스 중심 설계
- **Collection**: `/products` (복수형)
- **Item**: `/products/{productId}` (단수 ID)

### 7.2 HTTP 메서드 활용
- `GET`: 조회
- `POST`: 생성
- `PUT`: 전체 수정
- `PATCH`: 부분 수정
- `DELETE`: 삭제

### 7.3 상태 코드 활용
- `200 OK`: 성공
- `201 Created`: 생성 성공
- `204 No Content`: 성공 (응답 본문 없음)
- `400 Bad Request`: 잘못된 요청
- `401 Unauthorized`: 인증 실패
- `403 Forbidden`: 권한 없음
- `404 Not Found`: 리소스 없음
- `409 Conflict`: 충돌
- `500 Internal Server Error`: 서버 오류

### 7.4 버전 관리
- URI에 버전 포함: `/...`
- 하위 호환성 유지

### 7.5 페이징
- Query Parameter 사용: `?page=0&size=20`
- 응답에 페이징 정보 포함

### 7.6 필터링 및 정렬
- Query Parameter 사용: `?categoryId=1&sort=price,desc`

### 7.7 중첩 리소스
- 관계를 명확히 표현: `/users/{userId}/orders`

---

## 8. 보안 고려사항

### 8.1 인증/인가
- JWT 토큰 기반 인증
- Authorization Header: `Bearer {token}`

### 8.2 Rate Limiting
- API 호출 제한 (예: 100 req/min)
- 429 Too Many Requests 응답

### 8.3 HTTPS
- 모든 API는 HTTPS를 통해서만 접근 가능

### 8.4 입력 검증
- Request Body 검증 (@Valid)
- SQL Injection, XSS 방어

---

## 9. 멱등성 보장

### 9.1 주문 생성 API
- `idempotencyKey` 필수
- 동일한 키로 중복 요청 시 이미 생성된 주문 반환
- 키 유효 기간: 24시간

### 9.2 쿠폰 발급 API
- 동일 사용자가 동일 쿠폰 중복 발급 방지
- DB Unique Constraint 활용

---

## 10. 비동기 처리

### 10.1 외부 시스템 연동
- 주문 생성 시 외부 전송은 비동기로 처리
- 주문 생성은 동기로 완료
- 전송 실패 시 재시도 메커니즘

### 10.2 알림 발송
- 재입고 알림 등은 비동기로 처리
- 메시지 큐 활용

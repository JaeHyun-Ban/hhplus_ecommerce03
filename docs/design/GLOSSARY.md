# 용어사전 (Glossary)

이 문서는 E-Commerce 프로젝트에서 사용되는 Enum 상태값 및 주요 용어를 정리한 용어사전입니다.

---

## 📋 목차

- [사용자 (User)](#-사용자-user)
- [상품 (Product)](#-상품-product)
- [주문 (Order)](#-주문-order)
- [쿠폰 (Coupon)](#-쿠폰-coupon)
- [통합 이벤트 (Integration Event)](#-통합-이벤트-integration-event)

---

## 👤 사용자 (User)

### UserRole (사용자 역할)
사용자의 권한 레벨을 나타냅니다.

| 값 | 한글명 | 설명 |
|----|--------|------|
| `USER` | 일반 사용자 | 기본 권한을 가진 일반 사용자 |
| `ADMIN` | 관리자 | 시스템 관리 권한을 가진 사용자 |

**위치**: `domain.user.UserRole`

---

### UserStatus (사용자 상태)
사용자 계정의 상태를 나타냅니다.

| 값 | 한글명 | 설명 |
|----|--------|------|
| `ACTIVE` | 활성 | 정상적으로 활동 가능한 계정 |
| `INACTIVE` | 비활성 | 일시적으로 비활성화된 계정 |
| `DELETED` | 탈퇴 | 탈퇴 처리된 계정 |

**위치**: `domain.user.UserStatus`

---

### BalanceTransactionType (잔액 거래 유형)
사용자 잔액 거래의 유형을 나타냅니다.

| 값 | 한글명 | 설명 |
|----|--------|------|
| `CHARGE` | 충전 | 잔액 충전 |
| `USE` | 사용 | 잔액 사용 (결제 등) |
| `REFUND` | 환불 | 잔액 환불 |

**위치**: `domain.user.BalanceTransactionType`

---

## 🛍️ 상품 (Product)

### ProductStatus (상품 상태)
상품의 판매 가능 상태를 나타냅니다.

| 값 | 한글명 | 설명 |
|----|--------|------|
| `AVAILABLE` | 판매 가능 | 정상적으로 판매 가능한 상품 |
| `OUT_OF_STOCK` | 품절 | 재고가 소진된 상품 |
| `DISCONTINUED` | 단종 | 판매 중단된 상품 |

**위치**: `domain.product.ProductStatus`

---

### StockTransactionType (재고 거래 유형)
재고 변동의 유형을 나타냅니다.

| 값 | 한글명 | 설명 |
|----|--------|------|
| `INCREASE` | 증가 (입고) | 재고 입고로 인한 증가 |
| `DECREASE` | 감소 (판매) | 판매로 인한 재고 감소 |
| `ADJUSTMENT` | 조정 (재고 수정) | 재고 조사 등으로 인한 수동 조정 |

**위치**: `domain.product.StockTransactionType`

---

### NotificationStatus (알림 상태)
재입고 알림의 상태를 나타냅니다.

| 값 | 한글명 | 설명 |
|----|--------|------|
| `PENDING` | 대기 중 | 알림 발송 대기 중 |
| `SENT` | 발송 완료 | 알림이 성공적으로 발송됨 |
| `CANCELLED` | 취소됨 | 알림이 취소됨 |

**위치**: `domain.product.NotificationStatus`

---

## 📦 주문 (Order)

### OrderStatus (주문 상태)
주문의 처리 상태를 나타냅니다.

| 값 | 한글명 | 설명 |
|----|--------|------|
| `PENDING` | 결제 대기 | 주문 생성 후 결제 대기 중 |
| `PAID` | 결제 완료 | 결제가 완료된 주문 |
| `CANCELLED` | 취소 | 취소된 주문 |
| `REFUNDED` | 환불 | 환불 처리된 주문 |

**위치**: `domain.order.OrderStatus`

**상태 전이**:
```
PENDING → PAID → (완료)
   ↓        ↓
CANCELLED  REFUNDED
```

---

### PaymentMethod (결제 수단)
결제에 사용되는 수단을 나타냅니다.

| 값 | 한글명 | 설명 |
|----|--------|------|
| `BALANCE` | 잔액 결제 | 사용자 잔액을 사용한 결제 |

**위치**: `domain.order.PaymentMethod`

---

### PaymentStatus (결제 상태)
결제의 처리 상태를 나타냅니다.

| 값 | 한글명 | 설명 |
|----|--------|------|
| `PENDING` | 대기 중 | 결제 요청 대기 중 |
| `COMPLETED` | 완료 | 결제가 성공적으로 완료됨 |
| `FAILED` | 실패 | 결제 실패 |
| `CANCELLED` | 취소 | 결제 취소 |

**위치**: `domain.order.PaymentStatus`

**상태 전이**:
```
PENDING → COMPLETED
   ↓          ↓
FAILED    CANCELLED
```

---

## 🎟️ 쿠폰 (Coupon)

### CouponType (쿠폰 유형)
쿠폰의 할인 방식을 나타냅니다.

| 값 | 한글명 | 설명 |
|----|--------|------|
| `FIXED_AMOUNT` | 정액 할인 | 고정된 금액만큼 할인 (예: 5,000원 할인) |
| `PERCENTAGE` | 정률 할인 | 비율로 할인 (예: 10% 할인) |

**위치**: `domain.coupon.CouponType`

---

### CouponStatus (쿠폰 상태)
쿠폰의 발급 가능 상태를 나타냅니다.

| 값 | 한글명 | 설명 |
|----|--------|------|
| `ACTIVE` | 활성 | 발급 가능한 쿠폰 |
| `INACTIVE` | 비활성 | 발급 중단된 쿠폰 |
| `EXHAUSTED` | 소진됨 | 발급 수량이 모두 소진된 쿠폰 |

**위치**: `domain.coupon.CouponStatus`

---

### UserCouponStatus (사용자 쿠폰 상태)
사용자가 보유한 쿠폰의 상태를 나타냅니다.

| 값 | 한글명 | 설명 |
|----|--------|------|
| `ISSUED` | 발급됨 | 사용자에게 발급된 사용 가능한 쿠폰 |
| `USED` | 사용됨 | 이미 사용된 쿠폰 |
| `EXPIRED` | 만료됨 | 유효기간이 지난 쿠폰 |
| `REVOKED` | 회수됨 | 관리자에 의해 회수된 쿠폰 |

**위치**: `domain.coupon.UserCouponStatus`

**상태 전이**:
```
ISSUED → USED
  ↓
EXPIRED
  ↓
REVOKED
```

---

## 🔄 통합 이벤트 (Integration Event)

### EventType (이벤트 유형)
외부 시스템에 전달되는 이벤트의 유형을 나타냅니다.

| 값 | 한글명 | 설명 |
|----|--------|------|
| `ORDER_CREATED` | 주문 생성 | 새로운 주문이 생성됨 |
| `ORDER_CANCELLED` | 주문 취소 | 주문이 취소됨 |
| `ORDER_PAID` | 주문 결제 완료 | 주문 결제가 완료됨 |
| `ORDER_REFUNDED` | 주문 환불 | 주문이 환불 처리됨 |

**위치**: `domain.integration.EventType`

---

### EventStatus (이벤트 상태)
이벤트 전송의 처리 상태를 나타냅니다.

| 값 | 한글명 | 설명 |
|----|--------|------|
| `PENDING` | 대기 중 | 전송 대기 중인 이벤트 |
| `SENDING` | 전송 중 | 현재 전송 중인 이벤트 |
| `SUCCESS` | 성공 | 성공적으로 전송된 이벤트 |
| `FAILED` | 실패 | 전송 실패한 이벤트 (재시도 가능) |
| `DEAD_LETTER` | 최종 실패 (DLQ) | 재시도 횟수 초과로 DLQ로 이동된 이벤트 |

**위치**: `domain.integration.EventStatus`

**상태 전이**:
```
PENDING → SENDING → SUCCESS
             ↓
          FAILED (재시도)
             ↓
       DEAD_LETTER
```

---

## 📌 명명 규칙 (Naming Conventions)

### Enum 명명 규칙
- **Enum 클래스명**: PascalCase (예: `OrderStatus`, `PaymentMethod`)
- **Enum 값**: UPPER_SNAKE_CASE (예: `OUT_OF_STOCK`, `FIXED_AMOUNT`)

### 상태값 패턴
- **진행 상태**: `PENDING` → `PROCESSING` → `COMPLETED`
- **취소/실패 상태**: `CANCELLED`, `FAILED`
- **최종 상태**: `SUCCESS`, `COMPLETED`, `DEAD_LETTER`

---

## 🔗 관련 문서
- [도메인 모델 다이어그램](./domain-model-diagram.md)
- [API 명세서](./api-specification.md)
- [데이터베이스 스키마](./database-schema.md)

---

**최종 업데이트**: 2025-11-04
**문서 버전**: 1.0.0
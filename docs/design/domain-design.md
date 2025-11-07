# 도메인 및 엔티티 설계

## 1. 도메인 개요

### 주요 도메인
1. **User Domain** - 사용자 관리
2. **Product Domain** - 상품 관리
3. **Cart Domain** - 장바구니
4. **Order Domain** - 주문/결제
5. **Coupon Domain** - 쿠폰 관리
6. **Integration Domain** - 외부 시스템 연동

---

## 2. 엔티티 설계

### 2.1 User Domain

#### User (사용자)
```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal balance; // 잔액

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role; // ADMIN, USER

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status; // ACTIVE, INACTIVE, DELETED

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
```

**속성 설명:**
- `balance`: 결제에 사용할 사용자 잔액
- `role`: 사용자 권한 (관리자/일반 사용자)
- `status`: 계정 상태 (활성/비활성/탈퇴)

#### BalanceHistory (잔액 이력)
```java
@Entity
@Table(name = "balance_histories")
public class BalanceHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BalanceTransactionType type; // CHARGE, USE, REFUND

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private BigDecimal balanceBefore; // 변경 전 잔액

    @Column(nullable = false)
    private BigDecimal balanceAfter; // 변경 후 잔액

    private String description;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
```

**속성 설명:**
- `type`: 거래 유형 (충전/사용/환불)
- `balanceBefore/After`: 거래 전후 잔액 추적

---

### 2.2 Product Domain

#### Product (상품)
```java
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock; // 재고

    @Column(nullable = false)
    private Integer safetyStock; // 안전 재고 수준

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status; // AVAILABLE, OUT_OF_STOCK, DISCONTINUED

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Long version; // 낙관적 락을 위한 버전
}
```

**속성 설명:**
- `stock`: 현재 재고 수량 (동시성 제어 필요)
- `safetyStock`: 관리자 알림을 위한 안전 재고 임계값
- `version`: 재고 동시성 제어를 위한 낙관적 락

#### Category (카테고리)
```java
@Entity
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
```

#### StockHistory (재고 이력)
```java
@Entity
@Table(name = "stock_histories")
public class StockHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockTransactionType type; // INCREASE, DECREASE, ADJUSTMENT

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer stockBefore;

    @Column(nullable = false)
    private Integer stockAfter;

    private String reason; // 재고 변경 사유

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
```

#### ProductStatistics (상품 통계)
```java
@Entity
@Table(name = "product_statistics")
public class ProductStatistics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private LocalDate statisticsDate;

    @Column(nullable = false)
    private Integer salesCount; // 판매 수량

    @Column(nullable = false)
    private BigDecimal salesAmount; // 판매 금액

    @Column(nullable = false)
    private Integer viewCount; // 조회 수

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @UniqueConstraint(columnNames = {"product_id", "statistics_date"})
}
```

**속성 설명:**
- 최근 3일간 Top 5 상품 통계를 위한 사전 집계 테이블
- 실시간 계산 대신 배치로 집계하여 성능 개선

#### RestockNotification (재입고 알림)
```java
@Entity
@Table(name = "restock_notifications")
public class RestockNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status; // PENDING, SENT, CANCELLED

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime sentAt;

    @UniqueConstraint(columnNames = {"user_id", "product_id", "status"})
}
```

---

### 2.3 Cart Domain

#### Cart (장바구니)
```java
@Entity
@Table(name = "carts")
public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
```

#### CartItem (장바구니 항목)
```java
@Entity
@Table(name = "cart_items")
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private BigDecimal priceAtAdd; // 장바구니 담을 당시 가격

    @Column(nullable = false)
    private LocalDateTime addedAt;

    private LocalDateTime updatedAt;

    @UniqueConstraint(columnNames = {"cart_id", "product_id"})
}
```

**속성 설명:**
- `priceAtAdd`: 가격 변동 감지를 위해 장바구니에 담을 당시의 가격 저장

---

### 2.4 Order Domain

#### Order (주문)
```java
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNumber; // 주문 번호 (예: ORD-20231201-000001)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> orderItems = new ArrayList<>();

    @Column(nullable = false)
    private BigDecimal totalAmount; // 총 상품 금액

    @Column(nullable = false)
    private BigDecimal discountAmount; // 할인 금액

    @Column(nullable = false)
    private BigDecimal finalAmount; // 최종 결제 금액

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status; // PENDING, PAID, CANCELLED, REFUNDED

    @Column(nullable = false)
    private LocalDateTime orderedAt;

    private LocalDateTime paidAt;

    private LocalDateTime cancelledAt;

    private String cancellationReason;

    @Column(nullable = false, unique = true)
    private String idempotencyKey; // 중복 결제 방지를 위한 멱등성 키
}
```

**속성 설명:**
- `orderNumber`: 사용자 친화적인 주문 번호
- `idempotencyKey`: 중복 결제 방지를 위한 고유 키

#### OrderItem (주문 항목)
```java
@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String productName; // 주문 당시 상품명 (스냅샷)

    @Column(nullable = false)
    private BigDecimal price; // 주문 당시 가격 (스냅샷)

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private BigDecimal subtotal; // 소계 (가격 × 수량)
}
```

**속성 설명:**
- `productName`, `price`: 주문 당시의 상품 정보 스냅샷 (향후 상품 정보 변경 대비)

#### Payment (결제)
```java
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method; // BALANCE

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status; // PENDING, COMPLETED, FAILED, CANCELLED

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    private String failureReason;
}
```

---

### 2.5 Coupon Domain

#### Coupon (쿠폰 템플릿)
```java
@Entity
@Table(name = "coupons")
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code; // 쿠폰 코드

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type; // FIXED_AMOUNT, PERCENTAGE

    @Column(nullable = false)
    private BigDecimal discountValue; // 할인 값 (정액: 금액, 정률: %)

    private BigDecimal minimumOrderAmount; // 최소 주문 금액

    private BigDecimal maximumDiscountAmount; // 최대 할인 금액 (정률 쿠폰용)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category applicableCategory; // 적용 가능 카테고리 (null이면 전체)

    @Column(nullable = false)
    private Integer totalQuantity; // 총 발급 가능 수량

    @Column(nullable = false)
    private Integer issuedQuantity; // 이미 발급된 수량

    @Column(nullable = false)
    private Integer maxIssuePerUser; // 1인당 최대 발급 수

    @Column(nullable = false)
    private LocalDateTime issueStartAt; // 발급 시작 시간

    @Column(nullable = false)
    private LocalDateTime issueEndAt; // 발급 종료 시간

    @Column(nullable = false)
    private LocalDateTime validFrom; // 사용 가능 시작 시간

    @Column(nullable = false)
    private LocalDateTime validUntil; // 사용 가능 종료 시간

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status; // ACTIVE, INACTIVE, EXHAUSTED

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Long version; // 선착순 발급 동시성 제어
}
```

**속성 설명:**
- `type`: 정액 할인 또는 정률 할인
- `totalQuantity`, `issuedQuantity`: 선착순 발급 제어
- `version`: 동시 발급 시 동시성 제어

#### UserCoupon (사용자별 쿠폰)
```java
@Entity
@Table(name = "user_coupons")
public class UserCoupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserCouponStatus status; // ISSUED, USED, EXPIRED, REVOKED

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    private LocalDateTime usedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order usedOrder; // 사용된 주문

    private LocalDateTime expiredAt;

    private String revocationReason; // 회수 사유
}
```

**속성 설명:**
- `status`: 쿠폰 상태 (발급/사용/만료/회수)
- `usedOrder`: 쿠폰 사용 이력 추적

#### OrderCoupon (주문별 쿠폰 사용)
```java
@Entity
@Table(name = "order_coupons")
public class OrderCoupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_coupon_id", nullable = false)
    private UserCoupon userCoupon;

    @Column(nullable = false)
    private BigDecimal discountAmount; // 실제 할인된 금액

    @Column(nullable = false)
    private LocalDateTime appliedAt;
}
```

---

### 2.6 Integration Domain

#### OutboundEvent (외부 전송 이벤트)
```java
@Entity
@Table(name = "outbound_events")
public class OutboundEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType; // ORDER_CREATED, ORDER_CANCELLED, etc.

    @Column(nullable = false)
    private Long entityId; // 관련 엔티티 ID (Order ID 등)

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload; // JSON 형태의 전송 데이터

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status; // PENDING, SENDING, SUCCESS, FAILED, DEAD_LETTER

    @Column(nullable = false)
    private Integer retryCount; // 재시도 횟수

    @Column(nullable = false)
    private Integer maxRetryCount; // 최대 재시도 횟수

    private LocalDateTime nextRetryAt; // 다음 재시도 시간

    @Column(columnDefinition = "TEXT")
    private String errorMessage; // 실패 사유

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    private LocalDateTime completedAt;
}
```

**속성 설명:**
- `status`: 전송 상태 추적
- `retryCount`, `nextRetryAt`: 재시도 로직 관리
- `payload`: 전송할 데이터를 JSON 형태로 저장

---

## 3. 관계 다이어그램

### 3.1 핵심 도메인 관계
```
User (1) ─────── (1) Cart
  │                  │
  │                  │ (1:N)
  │                  └─────── CartItem ───── (N:1) ─────── Product
  │
  │ (1:N)
  ├─────── Order ───── (1:N) ─────── OrderItem ───── (N:1) ─────── Product
  │          │
  │          │ (1:1)
  │          ├─────── Payment
  │          │
  │          │ (1:N)
  │          └─────── OrderCoupon ───── (N:1) ─────── UserCoupon
  │
  │ (1:N)
  ├─────── UserCoupon ───── (N:1) ─────── Coupon
  │
  │ (1:N)
  ├─────── BalanceHistory
  │
  │ (1:N)
  └─────── RestockNotification ───── (N:1) ─────── Product
                                                      │
                                                      │ (1:N)
                                                      ├─────── StockHistory
                                                      │
                                                      │ (1:N)
                                                      ├─────── ProductStatistics
                                                      │
                                                      │ (N:1)
                                                      └─────── Category
```

### 3.2 이벤트 연동
```
Order ───── triggers ─────> OutboundEvent
  │
  └─── (생성/취소 시 외부 시스템으로 전송)
```

---

## 4. Enum 정의

### UserRole
```java
public enum UserRole {
    USER,    // 일반 사용자
    ADMIN    // 관리자
}
```

### UserStatus
```java
public enum UserStatus {
    ACTIVE,    // 활성
    INACTIVE,  // 비활성
    DELETED    // 탈퇴
}
```

### BalanceTransactionType
```java
public enum BalanceTransactionType {
    CHARGE,   // 충전
    USE,      // 사용
    REFUND    // 환불
}
```

### ProductStatus
```java
public enum ProductStatus {
    AVAILABLE,      // 판매 가능
    OUT_OF_STOCK,   // 품절
    DISCONTINUED    // 단종
}
```

### StockTransactionType
```java
public enum StockTransactionType {
    INCREASE,     // 증가 (입고)
    DECREASE,     // 감소 (판매)
    ADJUSTMENT    // 조정 (재고 수정)
}
```

### NotificationStatus
```java
public enum NotificationStatus {
    PENDING,    // 대기 중
    SENT,       // 발송 완료
    CANCELLED   // 취소됨
}
```

### OrderStatus
```java
public enum OrderStatus {
    PENDING,    // 결제 대기
    PAID,       // 결제 완료
    CANCELLED,  // 취소
    REFUNDED    // 환불
}
```

### PaymentMethod
```java
public enum PaymentMethod {
    BALANCE  // 잔액 결제
}
```

### PaymentStatus
```java
public enum PaymentStatus {
    PENDING,    // 대기 중
    COMPLETED,  // 완료
    FAILED,     // 실패
    CANCELLED   // 취소
}
```

### CouponType
```java
public enum CouponType {
    FIXED_AMOUNT,  // 정액 할인
    PERCENTAGE     // 정률 할인
}
```

### CouponStatus
```java
public enum CouponStatus {
    ACTIVE,     // 활성
    INACTIVE,   // 비활성
    EXHAUSTED   // 소진됨
}
```

### UserCouponStatus
```java
public enum UserCouponStatus {
    ISSUED,   // 발급됨
    USED,     // 사용됨
    EXPIRED,  // 만료됨
    REVOKED   // 회수됨
}
```

### EventType
```java
public enum EventType {
    ORDER_CREATED,     // 주문 생성
    ORDER_CANCELLED,   // 주문 취소
    ORDER_PAID,        // 주문 결제 완료
    ORDER_REFUNDED     // 주문 환불
}
```

### EventStatus
```java
public enum EventStatus {
    PENDING,      // 대기 중
    SENDING,      // 전송 중
    SUCCESS,      // 성공
    FAILED,       // 실패
    DEAD_LETTER   // 최종 실패 (DLQ)
}
```

---

## 5. 인덱스 전략

### 성능을 위한 주요 인덱스

```sql
-- User
CREATE INDEX idx_user_email ON users(email);
CREATE INDEX idx_user_status ON users(status);

-- Product
CREATE INDEX idx_product_category_status ON products(category_id, status);
CREATE INDEX idx_product_stock ON products(stock);

-- ProductStatistics
CREATE INDEX idx_product_statistics_date ON product_statistics(statistics_date, sales_count DESC);
CREATE INDEX idx_product_statistics_product ON product_statistics(product_id, statistics_date);

-- Order
CREATE INDEX idx_order_user ON orders(user_id, ordered_at DESC);
CREATE INDEX idx_order_status ON orders(status, ordered_at DESC);
CREATE INDEX idx_order_number ON orders(order_number);
CREATE INDEX idx_order_idempotency ON orders(idempotency_key);

-- UserCoupon
CREATE INDEX idx_user_coupon_user_status ON user_coupons(user_id, status);
CREATE INDEX idx_user_coupon_coupon ON user_coupons(coupon_id, status);

-- OutboundEvent
CREATE INDEX idx_outbound_event_status ON outbound_events(status, next_retry_at);
CREATE INDEX idx_outbound_event_type_entity ON outbound_events(event_type, entity_id);
```

---

## 6. 동시성 제어 전략

### 6.1 재고 관리
- **Optimistic Lock**: `Product.version` 필드 사용
- **Pessimistic Lock**: 높은 경합 시 `SELECT FOR UPDATE` 사용
- **분산 락**: Redis를 통한 분산 환경 동시성 제어

### 6.2 쿠폰 발급
- **Optimistic Lock**: `Coupon.version` 필드 사용
- **Redis 카운터**: 빠른 재고 확인을 위한 캐시
- **DB 트랜잭션**: 최종 발급 시 DB 정합성 보장

### 6.3 잔액 관리
- **Pessimistic Lock**: 잔액 차감 시 `SELECT FOR UPDATE` 사용
- **트랜잭션 격리 수준**: `REPEATABLE_READ` 이상

---

## 7. 데이터 정합성 보장

### 7.1 트랜잭션 범위
```
주문 생성 트랜잭션:
1. 재고 차감
2. 잔액 차감
3. 쿠폰 사용 처리
4. 주문 생성
5. 결제 생성
→ 하나라도 실패 시 전체 롤백
```

### 7.2 보상 트랜잭션
```
외부 전송 실패 시:
- 주문은 정상 처리 (PAID 상태 유지)
- OutboundEvent를 FAILED 상태로 기록
- 백그라운드 재시도 작업으로 처리
```

### 7.3 멱등성 보장
- `Order.idempotencyKey`: 동일한 요청에 대해 중복 주문 방지
- API 레벨에서 클라이언트가 제공하는 고유 키 사용

---

## 8. 확장 고려사항

### 8.1 향후 추가 가능한 엔티티
- **Address**: 배송지 관리
- **Review**: 상품 리뷰
- **Wishlist**: 관심 상품
- **Point**: 포인트 시스템
- **Notification**: 알림 이력

### 8.2 파티셔닝 전략
- **Order, OrderItem**: 날짜 기준 파티셔닝 (년/월)
- **ProductStatistics**: 날짜 기준 파티셔닝
- **OutboundEvent**: 상태/날짜 기준 파티셔닝

### 8.3 아카이빙 전략
- 오래된 주문 데이터는 별도 아카이브 테이블로 이동
- 완료된 OutboundEvent는 주기적으로 삭제 또는 아카이브
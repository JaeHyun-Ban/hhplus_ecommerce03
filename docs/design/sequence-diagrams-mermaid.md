# E-Commerce 시퀀스 다이어그램 (Mermaid)

> **이 파일을 https://mermaid.live 에서 시각화할 수 있습니다**

---

## 📋 목차

1. [주문 생성 및 결제](#1-주문-생성-및-결제)
2. [잔액 충전](#2-잔액-충전)
3. [장바구니에 상품 추가](#3-장바구니에-상품-추가)
4. [쿠폰 발급](#4-쿠폰-발급)
5. [주문 취소 및 환불](#5-주문-취소-및-환불)
6. [인기 상품 조회](#6-인기-상품-조회)
7. [재입고 알림](#7-재입고-알림)

---

## 1. 주문 생성 및 결제

### 전체 플로우

```mermaid
sequenceDiagram
    autonumber
    actor Client as 사용자
    participant API as OrderController
    participant Service as OrderService
    participant UserRepo as UserRepository
    participant ProductRepo as ProductRepository
    participant CouponRepo as CouponRepository
    participant OrderRepo as OrderRepository
    participant EventRepo as EventRepository
    participant DB as Database

    Client->>+API: POST /orders
    Note over Client,API: {userId, items, userCouponIds, idempotencyKey}

    API->>+Service: createOrder(request)

    rect rgb(240, 248, 255)
    Note over Service,DB: 1. 멱등성 체크
    Service->>OrderRepo: existsByIdempotencyKey(key)
    OrderRepo->>DB: SELECT * FROM orders WHERE idempotency_key = ?
    DB-->>OrderRepo: false
    OrderRepo-->>Service: 중복 없음
    end

    rect rgb(255, 250, 240)
    Note over Service,DB: 2. 트랜잭션 시작
    Service->>Service: @Transactional BEGIN
    end

    rect rgb(240, 255, 240)
    Note over Service,DB: 3. 사용자 조회
    Service->>UserRepo: findById(userId)
    UserRepo->>DB: SELECT * FROM users WHERE id = ?
    DB-->>UserRepo: User 엔티티
    UserRepo-->>Service: User
    end

    rect rgb(255, 245, 245)
    Note over Service,DB: 4. 재고 확인 및 차감
    loop 각 주문 상품
        Service->>ProductRepo: findByIdWithLock(productId)
        ProductRepo->>DB: SELECT * FROM products WHERE id = ? FOR UPDATE
        DB-->>ProductRepo: Product 엔티티
        ProductRepo-->>Service: Product

        Service->>Service: product.decreaseStock(quantity)
        Note over Service: stock -= quantity<br/>if (stock == 0) status = OUT_OF_STOCK

        Service->>ProductRepo: save(product)
        ProductRepo->>DB: UPDATE products SET stock = ?, status = ?
    end
    end

    rect rgb(255, 240, 255)
    Note over Service,DB: 5. 쿠폰 적용
    loop 각 쿠폰
        Service->>CouponRepo: findUserCouponById(userCouponId)
        CouponRepo->>DB: SELECT * FROM user_coupons WHERE id = ?
        DB-->>CouponRepo: UserCoupon
        CouponRepo-->>Service: UserCoupon

        Service->>Service: userCoupon.use(order)
        Note over Service: status = USED<br/>usedAt = now()

        Service->>Service: 할인 금액 계산
        Note over Service: discountAmount += coupon.calculateDiscountAmount()
    end
    end

    rect rgb(245, 245, 255)
    Note over Service,DB: 6. 잔액 확인 및 차감
    Service->>Service: user.useBalance(finalAmount)
    Note over Service: if (balance < finalAmount) throw Exception<br/>balance -= finalAmount

    Service->>UserRepo: save(user)
    UserRepo->>DB: UPDATE users SET balance = ?
    end

    rect rgb(250, 255, 240)
    Note over Service,DB: 7. 주문 생성
    Service->>Service: Order.builder()...build()
    Service->>OrderRepo: save(order)
    OrderRepo->>DB: INSERT INTO orders (...)
    DB-->>OrderRepo: orderId
    OrderRepo-->>Service: Order
    end

    rect rgb(255, 250, 245)
    Note over Service,DB: 8. 외부 연동 이벤트 생성
    Service->>Service: OutboundEvent.builder()...build()
    Note over Service: eventType = ORDER_CREATED<br/>status = PENDING
    Service->>EventRepo: save(outboundEvent)
    EventRepo->>DB: INSERT INTO outbound_events (...)
    end

    Service->>Service: @Transactional COMMIT

    Service-->>-API: OrderResponse
    API-->>-Client: 201 Created
    Note over Client,API: {orderId, orderNumber, finalAmount, status: PAID}

    rect rgb(240, 255, 255)
    Note over Service,DB: 9. 비동기 외부 전송
    Service->>Service: 백그라운드 작업 실행
    Service->>Service: 외부 API 호출
    alt 전송 성공
        Service->>EventRepo: markAsSuccess()
        EventRepo->>DB: UPDATE outbound_events SET status = 'SUCCESS'
    else 전송 실패
        Service->>EventRepo: markAsFailedAndScheduleRetry()
        EventRepo->>DB: UPDATE outbound_events SET status = 'FAILED', retry_count++
    end
    end
```

---

## 2. 잔액 충전

```mermaid
sequenceDiagram
    autonumber
    actor Client as 사용자
    participant API as UserController
    participant Service as UserService
    participant User as User Entity
    participant History as BalanceHistory
    participant UserRepo as UserRepository
    participant HistoryRepo as BalanceHistoryRepository
    participant DB as Database

    Client->>+API: POST /users/{userId}/balance/charge
    Note over Client,API: {amount: 10000}

    API->>+Service: chargeBalance(userId, amount)

    Service->>UserRepo: findById(userId)
    UserRepo->>DB: SELECT * FROM users WHERE id = ?
    DB-->>UserRepo: User 엔티티
    UserRepo-->>Service: User

    Service->>User: getBalance()
    User-->>Service: balanceBefore = 50000

    Service->>User: chargeBalance(10000)
    Note over User: balance += 10000<br/>balance = 60000

    Service->>User: getBalance()
    User-->>Service: balanceAfter = 60000

    Service->>History: BalanceHistory.of(user, CHARGE, ...)
    Note over History: type = CHARGE<br/>amount = 10000<br/>balanceBefore = 50000<br/>balanceAfter = 60000

    par 동시 저장
        Service->>UserRepo: save(user)
        UserRepo->>DB: UPDATE users SET balance = 60000 WHERE id = ?
    and
        Service->>HistoryRepo: save(balanceHistory)
        HistoryRepo->>DB: INSERT INTO balance_histories (...)
    end

    Service-->>-API: BalanceHistoryDto
    API-->>-Client: 200 OK
    Note over Client,API: {userId, balanceAfter: 60000, amount: 10000}
```

---

## 3. 장바구니에 상품 추가

```mermaid
sequenceDiagram
    autonumber
    actor Client as 사용자
    participant API as CartController
    participant Service as CartService
    participant CartRepo as CartRepository
    participant ProductRepo as ProductRepository
    participant Cart as Cart Entity
    participant CartItem as CartItem Entity
    participant DB as Database

    Client->>+API: POST /carts/{userId}/items
    Note over Client,API: {productId: 1, quantity: 2}

    API->>+Service: addItemToCart(userId, productId, quantity)

    Service->>CartRepo: findByUserId(userId)
    CartRepo->>DB: SELECT * FROM carts WHERE user_id = ?
    DB-->>CartRepo: Cart 엔티티
    CartRepo-->>Service: Cart

    Service->>ProductRepo: findById(productId)
    ProductRepo->>DB: SELECT * FROM products WHERE id = ?
    DB-->>ProductRepo: Product 엔티티
    ProductRepo-->>Service: Product

    Service->>Service: product.isAvailable()
    Note over Service: status == AVAILABLE && stock > 0

    Service->>Service: product.stock >= quantity?

    Service->>Cart: addItem(product, quantity)

    alt 상품이 이미 장바구니에 있음
        Cart->>CartItem: updateQuantity(기존수량 + 2)
        Note over CartItem: quantity = 5 (기존 3 + 신규 2)
    else 새로운 상품
        Cart->>CartItem: CartItem.builder()
        Note over CartItem: product, quantity = 2<br/>priceAtAdd = 10000
        Cart->>Cart: items.add(cartItem)
    end

    Service->>CartRepo: save(cart)
    CartRepo->>DB: UPDATE carts, INSERT/UPDATE cart_items

    Service-->>-API: CartItemDto
    API-->>-Client: 201 Created
    Note over Client,API: {cartItemId, productId, quantity, subtotal}
```

---

## 4. 쿠폰 발급

```mermaid
sequenceDiagram
    autonumber
    actor Client as 사용자
    participant API as CouponController
    participant Service as CouponService
    participant CouponRepo as CouponRepository
    participant UserCouponRepo as UserCouponRepository
    participant Coupon as Coupon Entity
    participant UserCoupon as UserCoupon Entity
    participant DB as Database

    Client->>+API: POST /coupons/{couponId}/issue
    Note over Client,API: {userId: 1}

    API->>+Service: issueCoupon(couponId, userId)

    rect rgb(255, 240, 240)
    Note over Service,DB: Pessimistic Lock 사용
    Service->>CouponRepo: findByIdWithLock(couponId)
    CouponRepo->>DB: SELECT * FROM coupons WHERE id = ? FOR UPDATE
    DB-->>CouponRepo: Coupon 엔티티 (잠금)
    CouponRepo-->>Service: Coupon
    end

    Service->>Coupon: canIssue()
    Note over Coupon: status == ACTIVE<br/>issuedQuantity < totalQuantity<br/>발급 기간 확인
    Coupon-->>Service: true

    Service->>UserCouponRepo: countByUserIdAndCouponId(userId, couponId)
    UserCouponRepo->>DB: SELECT COUNT(*) FROM user_coupons WHERE user_id = ? AND coupon_id = ?
    DB-->>UserCouponRepo: 0
    UserCouponRepo-->>Service: 0 (발급 가능)

    Service->>Coupon: issue()
    Note over Coupon: issuedQuantity++<br/>if (issuedQuantity >= totalQuantity)<br/>  status = EXHAUSTED

    Service->>UserCoupon: UserCoupon.builder()
    Note over UserCoupon: user, coupon<br/>status = ISSUED<br/>issuedAt = now()

    par 동시 저장
        Service->>CouponRepo: save(coupon)
        CouponRepo->>DB: UPDATE coupons SET issued_quantity = ?, status = ?
    and
        Service->>UserCouponRepo: save(userCoupon)
        UserCouponRepo->>DB: INSERT INTO user_coupons (...)
    end

    Service-->>-API: UserCouponDto
    API-->>-Client: 201 Created
    Note over Client,API: {userCouponId, couponCode, issuedAt}
```

---

## 5. 주문 취소 및 환불

```mermaid
sequenceDiagram
    autonumber
    actor Client as 사용자
    participant API as OrderController
    participant Service as OrderService
    participant OrderRepo as OrderRepository
    participant UserRepo as UserRepository
    participant ProductRepo as ProductRepository
    participant CouponRepo as UserCouponRepository
    participant DB as Database

    Client->>+API: POST /orders/{orderId}/cancel
    Note over Client,API: {reason: "단순 변심"}

    API->>+Service: cancelOrder(orderId, reason)

    Service->>Service: @Transactional BEGIN

    Service->>OrderRepo: findById(orderId)
    OrderRepo->>DB: SELECT * FROM orders WHERE id = ?
    DB-->>OrderRepo: Order
    OrderRepo-->>Service: Order

    Service->>Service: order.status == PAID?

    rect rgb(255, 245, 245)
    Note over Service,DB: 1. 잔액 환불
    Service->>UserRepo: findById(userId)
    UserRepo->>DB: SELECT * FROM users WHERE id = ?
    DB-->>UserRepo: User
    UserRepo-->>Service: User

    Service->>Service: user.refundBalance(finalAmount)
    Note over Service: balance += finalAmount

    Service->>UserRepo: save(user)
    UserRepo->>DB: UPDATE users SET balance = ?

    Service->>Service: BalanceHistory.of(user, REFUND, ...)
    end

    rect rgb(240, 255, 240)
    Note over Service,DB: 2. 재고 복구
    loop 각 주문 상품
        Service->>ProductRepo: findById(productId)
        ProductRepo->>DB: SELECT * FROM products WHERE id = ?
        DB-->>ProductRepo: Product
        ProductRepo-->>Service: Product

        Service->>Service: product.increaseStock(quantity)
        Note over Service: stock += quantity<br/>if (stock > 0) status = AVAILABLE

        Service->>ProductRepo: save(product)
        ProductRepo->>DB: UPDATE products SET stock = ?, status = ?
    end
    end

    rect rgb(255, 240, 255)
    Note over Service,DB: 3. 쿠폰 복구
    loop 각 사용된 쿠폰
        Service->>CouponRepo: findById(userCouponId)
        CouponRepo->>DB: SELECT * FROM user_coupons WHERE id = ?
        DB-->>CouponRepo: UserCoupon
        CouponRepo-->>Service: UserCoupon

        Service->>Service: userCoupon.restore()
        Note over Service: if (coupon.isValid())<br/>  status = ISSUED<br/>else<br/>  status = EXPIRED

        Service->>CouponRepo: save(userCoupon)
        CouponRepo->>DB: UPDATE user_coupons SET status = ?, used_at = NULL
    end
    end

    rect rgb(245, 245, 255)
    Note over Service,DB: 4. 주문 상태 변경
    Service->>Service: order.cancel(reason)
    Note over Service: status = CANCELLED<br/>cancelledAt = now()<br/>cancellationReason = reason

    Service->>OrderRepo: save(order)
    OrderRepo->>DB: UPDATE orders SET status = ?, cancelled_at = ?
    end

    Service->>Service: @Transactional COMMIT

    Service-->>-API: OrderDto
    API-->>-Client: 200 OK
    Note over Client,API: {orderId, status: CANCELLED}
```

---

## 6. 인기 상품 조회

```mermaid
sequenceDiagram
    autonumber
    actor Client as 사용자
    participant API as ProductController
    participant Service as ProductService
    participant Cache as Redis Cache
    participant StatsRepo as StatisticsRepository
    participant DB as Database

    Client->>+API: GET /products/popular?days=3&limit=5

    API->>+Service: getPopularProducts(days=3, limit=5)

    Service->>Cache: get("popular:3days:5")

    alt 캐시 히트
        Cache-->>Service: 인기 상품 리스트
        Note over Cache: 캐시된 데이터 반환
    else 캐시 미스
        Cache-->>Service: null

        Service->>StatsRepo: findPopularProducts(days, limit)
        StatsRepo->>DB: SELECT product_id, SUM(sales_count) as total<br/>FROM product_statistics<br/>WHERE statistics_date >= NOW() - INTERVAL 3 DAY<br/>GROUP BY product_id<br/>ORDER BY total DESC<br/>LIMIT 5
        DB-->>StatsRepo: 집계 결과
        StatsRepo-->>Service: List<PopularProductDto>

        Service->>Cache: set("popular:3days:5", result, TTL=10분)
        Note over Cache: 캐시에 저장 (10분 TTL)
    end

    Service-->>-API: List<PopularProductDto>
    API-->>-Client: 200 OK
    Note over Client,API: [{rank:1, productId:7, salesCount:203}...]
```

---

## 7. 재입고 알림

```mermaid
sequenceDiagram
    autonumber
    participant Scheduler as 배치 스케줄러
    participant Service as ProductService
    participant ProductRepo as ProductRepository
    participant NotifyRepo as NotificationRepository
    participant NotifyService as 알림 발송 서비스
    participant DB as Database

    Scheduler->>+Service: 재입고 처리 배치 (매일 실행)

    Service->>ProductRepo: findRestockScheduled()
    ProductRepo->>DB: SELECT * FROM products WHERE restock_scheduled = true
    DB-->>ProductRepo: List<Product>
    ProductRepo-->>Service: 재입고 예정 상품 리스트

    loop 각 상품
        Service->>Service: product.increaseStock(quantity)
        Note over Service: stock += quantity<br/>status = AVAILABLE

        Service->>ProductRepo: save(product)
        ProductRepo->>DB: UPDATE products SET stock = ?, status = ?

        Service->>NotifyRepo: findPendingNotifications(productId)
        NotifyRepo->>DB: SELECT * FROM restock_notifications<br/>WHERE product_id = ? AND status = 'PENDING'
        DB-->>NotifyRepo: List<RestockNotification>
        NotifyRepo-->>Service: 알림 대상자 리스트

        loop 각 알림 대상자
            Service->>NotifyService: sendNotification(user, product)

            alt 알림 발송 성공
                NotifyService-->>Service: 성공
                Service->>Service: notification.markAsSent()
                Note over Service: status = SENT<br/>sentAt = now()
                Service->>NotifyRepo: save(notification)
                NotifyRepo->>DB: UPDATE restock_notifications SET status = 'SENT'
            else 알림 발송 실패
                NotifyService-->>Service: 실패
                Note over Service: 로그 기록, 재시도 대기
            end
        end
    end

    Service-->>-Scheduler: 완료
```

---

## 🎨 다이어그램 사용 방법

### 1. Mermaid Live Editor
- https://mermaid.live 접속
- 위의 코드 블록 복사
- 에디터에 붙여넣기
- 실시간 미리보기 확인

### 2. GitHub/GitLab
- README나 이슈에 직접 붙여넣기
- 자동으로 렌더링됨

### 3. VS Code
- Mermaid 플러그인 설치
- Markdown Preview 사용

### 4. 문서 도구
- Notion: Mermaid 블록 지원
- Confluence: Mermaid 매크로 사용
- Obsidian: 기본 지원

---

## 📊 다이어그램 색상 의미

| 색상 | 의미 |
|------|------|
| 🔵 파란색 (rgb(240, 248, 255)) | 검증/체크 단계 |
| 🟡 노란색 (rgb(255, 250, 240)) | 트랜잭션 경계 |
| 🟢 초록색 (rgb(240, 255, 240)) | 조회 작업 |
| 🔴 빨간색 (rgb(255, 245, 245)) | 중요 비즈니스 로직 |
| 🟣 보라색 (rgb(255, 240, 255)) | 쿠폰/할인 관련 |
| 🔵 하늘색 (rgb(245, 245, 255)) | 결제/금액 관련 |
| 🟢 연두색 (rgb(250, 255, 240)) | 생성 작업 |
| 🟠 주황색 (rgb(255, 250, 245)) | 비동기/이벤트 |
| 🔵 청록색 (rgb(240, 255, 255)) | 외부 연동 |

---

## 🔧 커스터마이징

### 스타일 변경

```mermaid
%%{init: {'theme':'forest', 'themeVariables': { 'primaryColor':'#ff6666'}}}%%
sequenceDiagram
    ...
```

### 테마 옵션
- `default`: 기본 테마
- `forest`: 초록색 계열
- `dark`: 다크 모드
- `neutral`: 중립적인 색상

---

**Last Updated**: 2025-10-31
